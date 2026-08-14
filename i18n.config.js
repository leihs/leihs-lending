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
  supportedLngs: ["de", "de-CH", "gsw", "gsw-CH", "en", "en-GB", "es", "fr"],
  load: "languageOnly",
  fallbackLng: {
    gsw: ["de"],
    "gsw-CH": ["de"],
    "de-CH": ["de"],
    default: ["de"],
  },
})

// gsw is not supported by Intl; fr uses de-CH number/date formatting per ZHdK convention
const toIntlLocale = (lng) => {
  if (lng.startsWith("gsw") || lng.startsWith("fr")) return "de-CH"
  return lng
}

i18n.services.formatter.add("datetime", (value, lng, options) => {
  return new Intl.DateTimeFormat(toIntlLocale(lng), options).format(value)
})

i18n.services.formatter.add("price", (value, lng, options) => {
  return new Intl.NumberFormat(toIntlLocale(lng), options).format(value)
})

export { i18n }
export default i18n
