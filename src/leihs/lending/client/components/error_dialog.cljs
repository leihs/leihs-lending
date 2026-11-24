(ns leihs.lending.client.components.error-dialog
  (:require
   ["@@/alert-dialog" :refer [AlertDialogContent AlertDialogDescription
                              AlertDialogFooter AlertDialogHeader
                              AlertDialogTitle]]
   ["lucide-react" :refer [Ban Bomb CloudLightning SearchAlert
                           SquareChartGantt TriangleAlert]]
   ["react-i18next" :refer [useTranslation]]
   [clojure.string :as str]
   [leihs.lending.client.lib.errors :as errors]
   [uix.core :as uix :refer [$ defui]]))

(defui ^:private error-icon [{:keys [type]}]
  (case type
    :not-found ($ SearchAlert {:class-name "text-info"})
    :unauthorized ($ Ban {:class-name "text-yellow-600"})
    :forbidden ($ Ban {:class-name "text-error"})
    :bad-request ($ TriangleAlert {:class-name "text-yellow-600"})
    :server-error ($ CloudLightning {:class-name "text-error"})
    :network ($ CloudLightning {:class-name "text-yellow-600"})
    :application-error ($ TriangleAlert {:class-name "text-error"})
    ($ Bomb {:class-name "text-error"})))

(defn- details-text
  "Server messages and stack trace of `error`, nil if it has neither."
  [^js error]
  (let [server-messages (->> (some-> error .-graphQLErrors)
                             (keep (fn [^js e] (not-empty (.-message e)))))
        stack (or (some-> error .-stack) (some-> error .-message))]
    (some->> (cons (some->> (seq server-messages) (str/join "\n")) [stack])
             (remove nil?)
             seq
             (str/join "\n\n"))))

(defui ^:private error-details [{:keys [text]}]
  (let [[t] (useTranslation)]
    ($ :details {:class-name "mt-8"}
       ($ :summary {:class-name "cursor-pointer text-sm font-medium mb-2"}
          ($ SquareChartGantt {:class-name "inline size-4 mr-1 align-text-bottom"})
          (t "error.boundary.details"))
       ($ :div {:class-name "bg-muted p-4 rounded-md border overflow-auto max-h-64"}
          ($ :code {:class-name "text-xs text-foreground break-all whitespace-pre-wrap"}
             text)))))

(defui error-dialog-content
  "Content of an alert dialog describing `error`: icon, title and description
   (looked up under the i18n `scope`, e.g. \"error.boundary\"), status and
   technical details. `children` are the footer buttons."
  [{:keys [error scope children]}]
  (let [[t] (useTranslation)
        type (errors/error-type error)
        status (errors/status error)
        details (details-text error)
        key-prefix (str scope "." (str/replace (name type) "-" "_"))]
    ($ AlertDialogContent
       ($ AlertDialogHeader
          ($ AlertDialogTitle {:as-child true}
             ($ :h2 {:class-name "flex items-center"}
                ($ :span {:class-name "mr-2"}
                   ($ error-icon {:type type}))
                (t (str key-prefix ".title")))))

       ($ AlertDialogDescription {:as-child true}
          ($ :div
             ($ :p {:class-name "mb-6"}
                (t (str key-prefix ".description")))

             (when status
               ($ :p {:class-name "mb-6"}
                  (str "Status: " status)))

             (when details
               ($ error-details {:text details}))))

       ($ AlertDialogFooter children))))
