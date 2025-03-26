import React, { useContext, useState } from "react";
import Dropdown from "react-bootstrap/Dropdown";
import DownIcon from "../svg/DownIcon";
import LocaleContext from "../../IntlProvider";
import { locales, localeMap, languageMap } from "../../constants/locales";
import Cookies from "js-cookie";

export default function MultiLanguageDropdown() {
  const { onLocaleChange } = useContext(LocaleContext);
  const initialLocale = Cookies.get("wildbookLangCode") || "en";
  const [flag, setFlag] = useState(initialLocale);
  return (
    <div
      className="d-flex align-items-center justify-content-center border-0 rounded-pill m-2"
      style={{
        backgroundColor: "rgba(255, 255, 255, 0.25)",
        width: "65px",
        height: "35px",
      }}
    >
      <Dropdown>
        <Dropdown.Toggle variant="basic" id="dropdown-basic">
          <img
            src={`${process.env.PUBLIC_URL}/flags/${flag}.png`}
            alt="flag"
            style={{ width: "20px", height: "12px" }}
          />
          <span style={{ paddingLeft: 7 }}>
            <DownIcon />
          </span>
        </Dropdown.Toggle>

        <Dropdown.Menu>
          {locales.map((locale, index) => (
            <Dropdown.Item
              key={index}
              onClick={() => {
                onLocaleChange(locale);
                setFlag(localeMap[locale]);
              }}
            >
              <img
                src={`${process.env.PUBLIC_URL}/flags/${localeMap[locale]}.png`}
                alt={locale}
                style={{ width: "20px", height: "12px", marginRight: "10px" }}
              />
              {languageMap[locale]}
            </Dropdown.Item>
          ))}
        </Dropdown.Menu>
      </Dropdown>
    </div>
  );
}
