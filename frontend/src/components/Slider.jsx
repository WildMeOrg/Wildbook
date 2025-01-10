import React, { useState, useContext } from "react";
import Slider from "rc-slider";
import "rc-slider/assets/index.css";
import ThemeColorContext from "../ThemeColorProvider";

function RotationSlider({ setValue }) {
  const [angle, setAngle] = useState(0);
  const theme = useContext(ThemeColorContext);

  return (
    <div style={{ padding: "50px 100px 20px 100px", textAlign: "center" }}>
      <Slider
        min={-180}
        max={180}
        step={10}
        value={angle}
        onChange={(value) => {
          setAngle(value);
          setValue(value);
        }}
        marks={{
          "-180": "-180°",
          "-90": "-90°",
          0: "0°",
          90: "90°",
          180: "180°",
        }}
        railStyle={{ backgroundColor: "transparent", height: 8 }}
        trackStyle={{ backgroundColor: "transparent", height: 8 }}
        handleStyle={{
          backgroundColor: theme.primaryColors.primary500,
          borderColor: theme.primaryColors.primary500,
          width: "8px",
          height: "20px",
          borderRadius: "4px",
          marginTop: -8,
          //   backgroundColor: "#fff",
        }}
        dots={true}
        dotStyle={(dotValue) => {
          if (angle > 0 && angle <= 180 && dotValue <= angle && dotValue > 0) {
            return {
              backgroundColor: theme.primaryColors.primary500,
              borderColor: theme.primaryColors.primary500,
              width: "8px",
              height: "8px",
            };
          }

          if (angle >= -180 && angle < 0 && dotValue >= angle && dotValue < 0) {
            return {
              backgroundColor: theme.primaryColors.primary500,
              borderColor: theme.primaryColors.primary500,
              width: "8px",
              height: "8px",
            };
          }

          if (dotValue % 90 === 0) {
            return {
              backgroundColor: theme.grayColors.gray200,
              borderColor: theme.grayColors.gray200,
              width: "6px",
              height: "12px",
              borderRadius: "4px",
            };
          }
          if (dotValue === 0) {
            return {
              backgroundColor: theme.primaryColors.primary500,
              width: "14px",
              height: "14px",
            };
          }

          return {
            backgroundColor: theme.grayColors.gray200,
            borderColor: theme.grayColors.gray200,
            width: "8px",
            height: "8px",
          };
        }}
      />
    </div>
  );
}

export default RotationSlider;
