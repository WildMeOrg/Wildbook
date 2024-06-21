import React, { useEffect, useState } from "react";

const fetchScript = (src, id) => {
  return new Promise((resolve, reject) => {
    if (document.getElementById(id)) {
      resolve();
      return;
    }

    fetch(src)
      .then((response) => {
        if (!response.ok) {
          throw new Error(`Failed to load script: ${src}`);
        }
        const contentType = response.headers.get("content-type");
        if (!contentType || !contentType.includes("application/javascript")) {
          throw new Error(`Invalid content type for script: ${contentType}`);
        }
        return response.text();
      })
      .then((scriptContent) => {
        const script = document.createElement("script");
        script.id = id;
        script.async = true;
        script.textContent = scriptContent;
        document.head.appendChild(script);
        resolve();
      })
      .catch(reject);
  });
};

const AnalyticsAndTagManager = () => {
  const [error, setError] = useState(null);

  useEffect(() => {
    fetchScript("/JavascriptGlobals.js", "javascriptglobals")
      .then(() => {
        const gtmKey = window.wildbookGlobals.gtmKey || "changeme";
        const gaId = window.wildbookGlobals.gaId || "changeme";

        fetchScript(
          `https://www.googletagmanager.com/gtag/js?id=${gaId}`,
          "gtag-js",
        )
          .then(() => {
            const gtagConfigScript = document.createElement("script");
            gtagConfigScript.innerHTML = `
              window.dataLayer = window.dataLayer || [];
              function gtag(){dataLayer.push(arguments);}
              gtag('js', new Date());
              gtag('config', '${gaId}');
            `;
            document.head.appendChild(gtagConfigScript);

            const gtmScript = document.createElement("script");
            gtmScript.innerHTML = `
              (function (w, d, s, l, i) {
                w[l] = w[l] || []; w[l].push({
                  'gtm.start': new Date().getTime(), event: 'gtm.js'
                }); var f = d.getElementsByTagName(s)[0],
                  j = d.createElement(s), dl = l != 'dataLayer' ? '&l=' + l : ''; j.async = true; j.src =
                    'https://www.googletagmanager.com/gtm.js?id=' + i + dl; f.parentNode.insertBefore(j, f);
              })(window, document, 'script', 'dataLayer', '${gtmKey}');
            `;
            document.head.appendChild(gtmScript);
          })
          .catch((err) => {
            setError(err.message);
            console.error("Failed to load Google Analytics script:", err);
          });
      })
      .catch((err) => {
        setError(err.message);
        console.error("Failed to load JavascriptGlobals script:", err);
      });
  }, []);

  if (error) {
    return <div>Error loading scripts: {error}</div>;
  }

  return null;
};

export default AnalyticsAndTagManager;
