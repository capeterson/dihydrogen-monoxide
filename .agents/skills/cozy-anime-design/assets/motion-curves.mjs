// Local drawing units, normalized time 0..1. Transform around planted feet.
export const interval = (value, start, end) => Math.max(0, Math.min(1, (value - start) / (end - start)));
export const smoothStep = t => t * t * (3 - 2 * t);
export const pulse = (t, start, peak, end) => smoothStep(interval(t, start, peak)) * (1 - smoothStep(interval(t, peak, end)));
export function sipPose(t, bigHop = false) {
  if (t <= 0 || t >= 1) return {height: 0, stretch: 0, lean: 0, leafAngle: 0, delight: 0};
  const flight = interval(t, .35, .65), hop = 4 * flight * (1 - flight);
  const rebound = interval(t, .73, .84), settling = interval(t, .64, 1);
  return {
    height: (bigHop ? 24 : 16) * hop + 2.1 * 4 * rebound * (1 - rebound),
    stretch: -.17 * pulse(t, .16, .295, .36) + .14 * pulse(t, .295, .38, .49)
      + .07 * pulse(t, .52, .62, .65) - .2 * pulse(t, .63, .69, .76)
      + .045 * pulse(t, .71, .78, .83) - .035 * pulse(t, .81, .86, .94),
    lean: -6 * hop + 3 * pulse(t, .63, .71, .86),
    leafAngle: 11 * pulse(t, .18, .32, .41) - 26 * pulse(t, .31, .43, .59)
      + 14 * pulse(t, .49, .60, .68) - 16 * Math.sin(settling * 3 * Math.PI) * (1 - settling) ** 2,
    delight: pulse(t, .30, .48, .92),
  };
}
