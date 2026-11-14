const { setGlobalOptions } = require("firebase-functions/v2");
const { onValueCreated, onValueUpdated } = require("firebase-functions/v2/database");
const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { defineSecret } = require("firebase-functions/params");
const logger = require("firebase-functions/logger");
const { initializeApp } = require("firebase-admin/app");
const { getDatabase } = require("firebase-admin/database");
const { getMessaging } = require("firebase-admin/messaging");
const { calculateFare, isCoord } = require("./pricing");

// Must match your Realtime Database location (Firebase console → Realtime Database).
setGlobalOptions({ region: "us-central1", maxInstances: 10 });

initializeApp();
const db = () => getDatabase();

// Server-only key for the Directions web service. Set with:
//   firebase functions:secrets:set MAPS_SERVER_KEY
const MAPS_SERVER_KEY = defineSecret("MAPS_SERVER_KEY");

// ─── Pricing ────────────────────────────────────────────────────────────────
// The client writes an estimate; we overwrite it with the authoritative fare.
exports.priceTrip = onValueCreated("/trips/{tripId}", async (event) => {
  const trip = event.data.val();
  if (!trip) return;

  const fare = calculateFare(
    trip.pickupLat, trip.pickupLng, trip.dropoffLat, trip.dropoffLng, trip.rideType,
  );

  if (!fare) {
    logger.warn("priceTrip: unpriceable trip, cancelling", { tripId: event.params.tripId });
    await event.data.ref.update({ status: "cancelled", cancelReason: "invalid_trip" });
    return;
  }

  if (Math.abs((trip.price ?? 0) - fare.price) > 0.01) {
    logger.warn("priceTrip: client price differed from server price", {
      tripId: event.params.tripId, client: trip.price, server: fare.price,
    });
  }

  await event.data.ref.update({ ...fare, fareVerified: true });
});

// ─── Trip status → push notifications ───────────────────────────────────────
exports.onTripStatusChanged = onValueUpdated("/trips/{tripId}/status", async (event) => {
  const before = event.data.before.val();
  const after = event.data.after.val();
  if (before === after) return;

  const { tripId } = event.params;
  const tripRef = event.data.after.ref.parent;
  const trip = (await tripRef.get()).val();
  if (!trip) return;

  switch (after) {
    case "accepted": {
      // Don't trust driver details written by the driver's client.
      const driver = (await db().ref(`users/${trip.driverId}`).get()).val() || {};
      if (driver.role !== "driver") {
        logger.error("onTripStatusChanged: non-driver accepted trip, reverting", { tripId });
        await tripRef.update({ status: "searching", driverId: "" });
        return;
      }
      const driverName = driver.displayName || "Your driver";
      await tripRef.update({
        driverName,
        driverRating: driver.rating ?? 5,
        driverPhotoUrl: driver.photoUrl || "",
        driverVehicle: driver.vehicle || "",
        driverPlate: driver.plate || "",
      });
      await notify(trip.passengerId, {
        title: "Driver on the way",
        body: `${driverName} accepted your trip and is heading to you.`,
        type: "DRIVER_ACCEPTED", tripId, role: "passenger",
      });
      break;
    }
    case "arrived":
      await notify(trip.passengerId, {
        title: "Your driver has arrived",
        body: `${trip.driverName || "Your driver"} is waiting at the pickup point.`,
        type: "DRIVER_ARRIVED", tripId, role: "passenger",
      });
      break;
    case "completed":
      await notify(trip.passengerId, {
        title: "Trip completed",
        body: `You've arrived. Total: ${Number(trip.price || 0).toFixed(2)} BYN`,
        type: "TRIP_COMPLETED", tripId, role: "passenger",
      });
      break;
    case "cancelled":
      if (trip.driverId) {
        await notify(trip.driverId, {
          title: "Trip cancelled",
          body: "The rider cancelled this trip.",
          type: "TRIP_CANCELLED", tripId, role: "driver",
        });
      }
      break;
  }
});

async function notify(uid, { title, body, type, tripId, role }) {
  if (!uid) return;
  const tokenRef = db().ref(`users/${uid}/fcmToken`);
  const token = (await tokenRef.get()).val();
  if (!token) {
    logger.info("notify: no FCM token", { uid, type });
    return;
  }
  try {
    await getMessaging().send({
      token,
      notification: { title, body },
      data: { type, trip_id: tripId, role },
      android: { priority: "high", notification: { channelId: "trip_channel" } },
    });
  } catch (err) {
    if (err.code === "messaging/registration-token-not-registered") {
      await tokenRef.remove();
    } else {
      logger.error("notify failed", { uid, type, err: err.message });
    }
  }
}

// ─── Routes (keeps the Directions key off the device) ───────────────────────
exports.getRoute = onCall({ secrets: [MAPS_SERVER_KEY] }, async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Sign in required.");

  const { originLat, originLng, destLat, destLng } = request.data || {};
  if (!isCoord(originLat, originLng) || !isCoord(destLat, destLng)) {
    throw new HttpsError("invalid-argument", "Invalid coordinates.");
  }

  const url = new URL("https://maps.googleapis.com/maps/api/directions/json");
  url.searchParams.set("origin", `${originLat},${originLng}`);
  url.searchParams.set("destination", `${destLat},${destLng}`);
  url.searchParams.set("mode", "driving");
  url.searchParams.set("key", MAPS_SERVER_KEY.value());

  const res = await fetch(url);
  const json = await res.json();
  if (json.status !== "OK" || !json.routes?.length) {
    logger.warn("getRoute: directions failed", { status: json.status });
    throw new HttpsError("unavailable", `Directions: ${json.status}`);
  }

  const route = json.routes[0];
  const leg = route.legs?.[0];
  return {
    points: route.overview_polyline.points,
    distanceMeters: leg?.distance?.value ?? 0,
    durationSeconds: leg?.duration?.value ?? 0,
  };
});
