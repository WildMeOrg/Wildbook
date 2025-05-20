import { useEffect } from "react";

const loadScript = (src, id) => {
  return new Promise((resolve, reject) => {
    if (document.getElementById(id)) {
      resolve();
      return;
    }
    const script = document.createElement("script");
    script.src = src;
    script.id = id;
    script.async = true;
    script.onload = resolve;
    script.onerror = reject;
    document.head.appendChild(script);
  });
};

const AnalyticsAndTagManager = () => {
  useEffect(() => {
    loadScript("/JavascriptGlobals.js", "javascriptglobals")
      .then(() => {
        const gtmKey = window.wildbookGlobals?.gtmKey || "changeme";
        const gaId = window.wildbookGlobals?.gaId || "changeme";

        loadScript(
          `https://www.googletagmanager.com/gtag/js?id=${gaId}`,
          "gtag-js",
        );

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
        console.error("Failed to load script:", err);
      });
  }, []);

  return null;
};

export default AnalyticsAndTagManager;
