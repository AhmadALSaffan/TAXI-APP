// Exercises ../database.rules.json against the RTDB emulator over REST.
// Run: npm run test:rules   (needs Java on PATH for the emulator)
const HOST = "http://127.0.0.1:9000";
const NS = "taxiapp-e2a5f-default-rtdb";
const PROJECT = "taxiapp-e2a5f";

async function req(who, method, path, body) {
  const headers = { "Content-Type": "application/json" };
  let qs = `ns=${NS}`;
  if (who) headers.Authorization = "Bearer owner";
  // Admin credential + auth_variable_override = evaluate rules as that user.
  if (who && who !== "admin") qs += `&auth_variable_override=${encodeURIComponent(JSON.stringify({ uid: who }))}`;
  const res = await fetch(`${HOST}/${path}.json?${qs}`, {
    method, headers, body: body === undefined ? undefined : JSON.stringify(body),
  });
  return res.status;
}

let failed = 0;
async function expect(label, allowed, p) {
  const status = await p;
  const ok = allowed ? status === 200 : status === 401 || status === 403;
  if (!ok) failed++;
  console.log(`${ok ? "PASS" : "FAIL"}  ${allowed ? "allow" : "deny "}  ${label}  (HTTP ${status})`);
}

const baseTrip = {
  tripId: "t1", passengerId: "pax", passengerName: "", pickupAddress: "A", dropoffAddress: "B",
  pickupLat: 40.71, pickupLng: -74.0, dropoffLat: 40.75, dropoffLng: -73.98, rideType: "economy",
  price: 9.5, baseFare: 2.5, distanceFare: 5, timeFare: 2, tolls: 0, distanceMiles: 3, durationMinutes: 8,
  paymentMethod: "cash", status: "searching", createdAt: Date.now() - 1000, startedAt: 0, completedAt: 0,
  driverId: "", driverName: "", driverRating: 0, driverVehicle: "", driverPlate: "", driverPhotoUrl: "",
  driverLat: 0, driverLng: 0, cashPaid: false,
};

(async () => {
  await req("admin", "PUT", "", {
    config: { privateKey: "secret" },
    users: {
      pax: { role: "passenger", rating: 5 },
      drv: { role: "driver", rating: 4.9, displayName: "Real Driver" },
      drv2: { role: "driver", rating: 4.8 },
      eve: { role: "passenger", rating: 5 },
    },
  });

  console.log("— config / users");
  await expect("anyone reads /config", false, req("pax", "GET", "config"));
  await expect("unauthenticated reads /users", false, req(null, "GET", "users"));
  await expect("user reads own profile", true, req("pax", "GET", "users/pax"));
  await expect("user reads someone else's profile", false, req("eve", "GET", "users/pax"));
  await expect("user saves own fcmToken", true, req("pax", "PUT", "users/pax/fcmToken", "tok"));
  await expect("passenger promotes self to driver", false, req("pax", "PUT", "users/pax/role", "driver"));
  await expect("user edits own rating", false, req("pax", "PUT", "users/pax/rating", 1));
  await expect("new user signs up as passenger", true, req("new", "PUT", "users/new", { role: "passenger", rating: 5 }));
  await expect("new user signs up as driver", false, req("new2", "PUT", "users/new2", { role: "driver", rating: 5 }));

  console.log("— trip creation");
  await expect("passenger creates trip for someone else", false, req("eve", "PUT", "trips/t1", baseTrip));
  await expect("passenger creates trip pre-assigned to a driver", false,
    req("pax", "PUT", "trips/t1", { ...baseTrip, driverId: "drv" }));
  await expect("passenger creates trip already 'completed'", false,
    req("pax", "PUT", "trips/t1", { ...baseTrip, status: "completed" }));
  await expect("passenger creates trip with junk field", false,
    req("pax", "PUT", "trips/t1", { ...baseTrip, isFree: true }));
  await expect("passenger creates valid trip", true, req("pax", "PUT", "trips/t1", baseTrip));

  console.log("— reading trips");
  await expect("passenger reads own trip", true, req("pax", "GET", "trips/t1"));
  await expect("other passenger reads trip", false, req("eve", "GET", "trips/t1"));
  await expect("other passenger lists all trips", false, req("eve", "GET", "trips"));
  await expect("driver lists trips", true, req("drv", "GET", "trips"));

  console.log("— tampering");
  await expect("passenger lowers price", false, req("pax", "PUT", "trips/t1/price", 0.01));
  await expect("passenger changes dropoff", false, req("pax", "PUT", "trips/t1/dropoffLat", 41));
  await expect("passenger marks own trip completed", false, req("pax", "PUT", "trips/t1/status", "completed"));
  await expect("passenger accepts own trip", false,
    req("pax", "PATCH", "trips/t1", { status: "accepted", driverId: "pax" }));
  await expect("non-driver deletes trip", false, req("eve", "DELETE", "trips/t1"));
  await expect("random driver cancels searching trip", false, req("drv2", "PUT", "trips/t1/status", "cancelled"));

  console.log("— driver flow");
  await expect("driver accepts trip as someone else", false,
    req("drv", "PATCH", "trips/t1", { status: "accepted", driverId: "drv2" }));
  await expect("driver accepts trip", true,
    req("drv", "PATCH", "trips/t1", { status: "accepted", driverId: "drv", driverName: "D", driverLat: 40.7, driverLng: -74 }));
  await expect("second driver steals accepted trip", false,
    req("drv2", "PATCH", "trips/t1", { driverId: "drv2" }));
  await expect("second driver reads accepted trip directly", true, req("drv2", "GET", "trips/t1"));
  await expect("passenger spoofs driver location", false, req("pax", "PUT", "trips/t1/driverLat", 1));
  await expect("driver updates location (multi-path)", true,
    req("drv", "PATCH", "", { "drivers/drv/location/lat": 40.71, "drivers/drv/location/lng": -74, "trips/t1/driverLat": 40.71 }));
  await expect("passenger writes to /drivers", false, req("pax", "PUT", "drivers/pax/available", true));
  await expect("driver changes fare after accepting", false, req("drv", "PUT", "trips/t1/price", 99));
  await expect("driver marks arrived", true, req("drv", "PUT", "trips/t1/status", "arrived"));
  await expect("driver completes trip", true,
    req("drv", "PATCH", "trips/t1", { status: "completed", completedAt: Date.now() }));
  await expect("passenger reopens completed trip", false, req("pax", "PUT", "trips/t1/status", "searching"));
  await expect("driver marks cash paid", true, req("drv", "PUT", "trips/t1/cashPaid", true));

  console.log("— cancellation");
  await req("admin", "PUT", "trips/t2", { ...baseTrip, tripId: "t2" });
  await expect("passenger cancels own searching trip", true, req("pax", "PUT", "trips/t2/status", "cancelled"));

  console.log(failed ? `\n${failed} FAILED` : "\nall rules checks passed");
  process.exit(failed ? 1 : 0);
})().catch((e) => { console.error(e); process.exit(1); });
