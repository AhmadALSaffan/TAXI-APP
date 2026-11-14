const test = require("node:test");
const assert = require("node:assert");
const { calculateFare } = require("./pricing");

test("short trip falls back to the minimum fare", () => {
  const f = calculateFare(53.9006, 27.559, 53.9016, 27.559, "economy");
  assert.strictEqual(f.price, 4.5);
  assert.strictEqual(f.durationMinutes, 1);
});

test("longer trip is priced per km + minute in BYN, with no tolls", () => {
  // Minsk centre to the airport area, ~ 37 km in a straight line.
  const f = calculateFare(53.9006, 27.559, 53.8825, 28.0307, "premium");
  assert.strictEqual(f.tolls, 0);
  const km = f.distanceMiles / 0.621371;
  const expected = 3.0 + km * 0.9 + f.durationMinutes * 0.22;
  assert.ok(Math.abs(f.price - expected) < 0.05, `${f.price} vs ${expected}`);
});

test("economy is cheaper than premium, premium cheaper than XL", () => {
  const args = [53.9006, 27.559, 53.93, 27.65];
  const e = calculateFare(...args, "economy").price;
  const p = calculateFare(...args, "premium").price;
  const x = calculateFare(...args, "xl").price;
  assert.ok(e < p && p < x, `${e} ${p} ${x}`);
});

test("rejects unknown ride types and bad coordinates", () => {
  assert.strictEqual(calculateFare(1, 1, 2, 2, "helicopter"), null);
  assert.strictEqual(calculateFare(0, 0, 2, 2, "economy"), null);
  assert.strictEqual(calculateFare(NaN, 1, 2, 2, "economy"), null);
  assert.strictEqual(calculateFare(1, 1, 200, 2, "economy"), null);
});
