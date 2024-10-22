// import { ProcaptchaFrictionless } from "@prosopo/procaptcha-frictionless";
// import { ProcaptchaPow } from "@prosopo/procaptcha-pow";
// import { Procaptcha } from "@prosopo/procaptcha-react";

// import {
// 	ProcaptchaConfigSchema,
// } from "@prosopo/types";

// const config = ProcaptchaConfigSchema.parse({
// 	account: {
// 		address: process.env.PROSOPO_SITE_KEY,
// 	},
// 	// web2: process.env.PROSOPO_WEB2 !== "false",
// 	// defaultEnvironment: process.env.PROSOPO_DEFAULT_ENVIRONMENT,
// 	dappName: "client-example",
// 	serverUrl: getServerUrl(),
// 	theme: "light",
// });



// const onError = (error) => {
// 	alert(error.message);
// };

// const onExpired = () => {
// 	alert("Challenge has expired");
// };

// export function Captcha(props) {
// // type CaptchProps = {
// // 	captchaType?: string;
// // 	onHuman: (procaptchaToken: ProcaptchaToken) => void;
// // 	key: number;
// // };
// 	const onHuman = async (procaptchaToken) => {
// 		console.log("onHuman", procaptchaToken);
// 		props.onHuman(procaptchaToken);
// 	};

// 	const onFailed = () => {
// 		console.log("The user failed the captcha");
// 	};

// 	return (
// 		<div>
// 			{props.captchaType === "frictionless" ? (
// 				<ProcaptchaFrictionless
// 					config={config}
// 					callbacks={{ onError, onHuman, onExpired, onFailed }}
// 					aria-label="Frictionless captcha"
// 				/>
// 			) : props.captchaType === "pow" ? (
// 				<ProcaptchaPow
// 					config={config}
// 					callbacks={{ onError, onHuman, onExpired, onFailed }}
// 					aria-label="PoW captcha"
// 				/>
// 			) : (
// 				<Procaptcha
// 					config={config}
// 					callbacks={{ onError, onHuman, onExpired, onFailed }}
// 					aria-label="Image captcha"
// 				/>
// 			)}
// 		</div>
// 	);
// }