export default function calculateScaleFactor(
  naturalWidth,
  naturalHeight,
  displayWidth,
  displayHeight,
) {
  const scaleX = naturalWidth / displayWidth;
  const scaleY = naturalHeight / displayHeight;

  return { x: scaleX, y: scaleY };
}
