import i18n from "i18next"
import { initReactI18next } from "react-i18next"
import de from "./resources/public/lending/assets/locales/de/translation.json"
import en from "./resources/public/lending/assets/locales/en/translation.json"

const resources = {
  de: { translation: de },
  en: { translation: en },
}

i18n.use(initReactI18next).init({
  resources,
  lng: "de",
  supportedLngs: ["de", "de-CH", "gsw", "gsw-CH", "en", "en-GB"],
  load: "languageOnly",
  fallbackLng: {
    gsw: ["de"],
    "gsw-CH": ["de"],
    "de-CH": ["de"],
    default: ["de"],
  },
})

export { i18n }
export default i18n
