import assert from 'node:assert/strict';
import {sipPose, pulse} from '../assets/motion-curves.mjs';
const rest = {height: 0, stretch: 0, lean: 0, leafAngle: 0, delight: 0};
assert.deepEqual(sipPose(0), rest);
assert.deepEqual(sipPose(1), rest);
assert(sipPose(.29).stretch < 0, 'anticipation should compress');
assert(sipPose(.38).stretch > 0, 'takeoff should stretch');
assert(sipPose(.69).stretch < 0, 'landing should compress');
assert(sipPose(.5, true).height > sipPose(.5).height, 'goal hop should be taller');
assert(sipPose(.78).height < sipPose(.5).height, 'rebound should be smaller');
assert.deepEqual(sipPose(.69, true), sipPose(.69), 'landing should remain grounded');
for (let frame = 0; frame <= 1000; frame++) {
  const pose = sipPose(frame / 1000);
  assert(Object.values(pose).every(Number.isFinite), 'all poses must be finite');
  assert(pose.height >= 0, 'the body must not fall below its surface');
  assert(Math.abs(pose.stretch) <= .25, 'squash/stretch should remain restrained');
  assert(pulse(frame / 1000, .1, .4, 1) >= 0);
}
console.log('Pose endpoints, anticipation, takeoff, landing, rebound and 1001 intermediate frames passed.');
