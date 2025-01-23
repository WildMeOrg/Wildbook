export default function calculateFinalRect(rect, scaleFactor, value) {
  const radians = (value * Math.PI) / 180;
  const halfW = rect.width / 2;
  const halfH = rect.height / 2;

  const theta0 = Math.atan(halfH / halfW);
  const radius = Math.sqrt(halfW * halfW + halfH * halfH);

  const a = Math.cos(radians + theta0) * radius;
  const b = Math.sin(radians + theta0) * radius;

  const cx = rect.x + a;
  const cy = rect.y + b;

  const x = cx - halfW;
  const y = cy - halfH;

  return {
    x: x * scaleFactor.x,
    y: y * scaleFactor.y,
    width: rect.width * scaleFactor.x,
    height: rect.height * scaleFactor.y,
    rotation: radians,
  };
}
