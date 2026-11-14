// Server-side port of app/src/main/java/com/taxiapp/util/Pricingengine.kt.
// The app shows an estimate; this is the price that is actually charged.
// Keep both in sync until the app fetches quotes from the server.
// Prices are in Belarusian roubles (BYN), per kilometre and minute.

const RATES = {
  economy: { baseFare: 2.0, perKm: 0.65, perMinute: 0.15, minimumFare: 4.5 },
  premium: { baseFare: 3.0, perKm: 0.9, perMinute: 0.22, minimumFare: 6.5 },
  xl: { baseFare: 3.5, perKm: 1.0, perMinute: 0.25, minimumFare: 7.5 },
};

const r2 = (v) => Math.round(v * 100) / 100;
const toRad = (deg) => (deg * Math.PI) / 180;

function haversineKm(lat1, lng1, lat2, lng2) {
  const dLat = toRad(lat2 - lat1);
  const dLng = toRad(lng2 - lng1);
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLng / 2) ** 2;
  return 6371 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

function estimateDurationMinutes(distanceKm) {
  return Math.max(1, Math.trunc((distanceKm / 30) * 60));
}

function isCoord(lat, lng) {
  return (
    Number.isFinite(lat) && Number.isFinite(lng) &&
    Math.abs(lat) <= 90 && Math.abs(lng) <= 180 &&
    !(lat === 0 && lng === 0)
  );
}

/** Returns null when the input can't be priced (unknown ride type, bad coordinates). */
function calculateFare(pickupLat, pickupLng, dropoffLat, dropoffLng, rideType) {
  const tier = RATES[rideType];
  if (!tier || !isCoord(pickupLat, pickupLng) || !isCoord(dropoffLat, dropoffLng)) return null;

  const distanceKm = haversineKm(pickupLat, pickupLng, dropoffLat, dropoffLng);
  // Trips store miles for compatibility with older records; the app shows km.
  const distanceMiles = distanceKm * 0.621371;
  const durationMinutes = estimateDurationMinutes(distanceKm);

  const distanceFare = distanceKm * tier.perKm;
  const timeFare = durationMinutes * tier.perMinute;
  const tolls = 0;
  const subtotal = tier.baseFare + distanceFare + timeFare + tolls;

  return {
    baseFare: r2(tier.baseFare),
    distanceFare: r2(distanceFare),
    timeFare: r2(timeFare),
    tolls: r2(tolls),
    price: r2(Math.max(subtotal, tier.minimumFare)),
    distanceMiles: r2(distanceMiles),
    durationMinutes,
  };
}

module.exports = { calculateFare, isCoord, RATES };
