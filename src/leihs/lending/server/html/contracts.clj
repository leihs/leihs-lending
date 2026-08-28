(ns leihs.lending.server.html.contracts
  (:require
   [clj-time.core :as time]
   [clj-time.format :as time-format]
   [clojure.string :as str]
   [hiccup2.core :as hiccup]
   [leihs.core.languages :as lang]
   [leihs.core.settings :refer [settings]]
   [leihs.core.user.permissions :as permissions]
   [leihs.lending.server.i18n :refer [t]]
   [leihs.lending.server.resources.accessories :as accessories]
   [leihs.lending.server.resources.contracts :as contracts-resource]
   [leihs.lending.server.resources.inventory-pools :as inventory-pools]
   [leihs.lending.server.resources.items :as items]
   [leihs.lending.server.resources.orders :as orders]
   [leihs.lending.server.resources.reservations :as reservations]
   [leihs.lending.server.resources.users :as users])
  (:import
   [com.google.zxing BarcodeFormat]
   [com.google.zxing.client.j2se MatrixToImageWriter]
   [com.google.zxing.oned Code128Writer]
   [java.io ByteArrayOutputStream]
   [java.time LocalDate]
   [java.time.format DateTimeFormatter]
   [java.time.temporal WeekFields]
   [java.util Base64]))

(defn- authorized? [tx pool-id authenticated-entity contract]
  (let [user-id (:id authenticated-entity)]
    (or (= user-id (:user_id contract))
        (users/delegated-user-of? tx user-id (:user_id contract))
        (permissions/manager-in-pool? pool-id authenticated-entity))))

(def ^:private date-formatter (time-format/formatter "dd.MM.yyyy"))

(defn- ->local-date
  "clj-time.coerce's to-date-time/to-local-date shift a java.sql.Date by a day
  when the JVM's default timezone is behind UTC (it round-trips through an
  epoch-millis instant) -- read the calendar fields directly instead."
  [v]
  (cond
    (instance? org.joda.time.LocalDate v) v
    (instance? java.sql.Timestamp v)
    (let [ld (.toLocalDate (.toLocalDateTime ^java.sql.Timestamp v))]
      (time/local-date (.getYear ld) (.getMonthValue ld) (.getDayOfMonth ld)))

    (instance? java.sql.Date v)
    (let [ld (.toLocalDate ^java.sql.Date v)]
      (time/local-date (.getYear ld) (.getMonthValue ld) (.getDayOfMonth ld)))

    :else v))

(defn- format-date [date]
  (when date (time-format/unparse-local-date date-formatter (->local-date date))))

(defn- week-of-year [date]
  (time/week-number-of-year (->local-date date)))

(defn- barcode-data-uri
  "Code128 barcode PNG, base64-embedded -- mirrors legacy's
  barcode_for_contract (Barby::Code128B, height: 25, ~1px/module), including
  the ' C ' prefix in case downstream scanning tooling depends on it to
  identify contract barcodes. Width 0 makes zxing auto-size to the minimal
  width the content needs at 1px/module, matching Barby's default."
  [compact-id]
  (let [content (str " C " compact-id)
        matrix (.encode (Code128Writer.) content BarcodeFormat/CODE_128 0 25)
        baos (ByteArrayOutputStream.)]
    (MatrixToImageWriter/writeToStream matrix "PNG" baos)
    (str "data:image/png;base64," (.encodeToString (Base64/getEncoder) (.toByteArray baos)))))

(defn- group-thousands [int-str]
  (->> int-str
       reverse
       (partition-all 3)
       (map (fn [chunk] (apply str (reverse chunk))))
       reverse
       (str/join "'")))

(defn- format-currency [n]
  (let [[int-part dec-part] (str/split (format "%.2f" (double n)) #"\.")]
    (str (group-thousands int-part) "." dec-part)))

(defn- nl->br
  "Legacy renders this text through Rails' simple_format, which turns
  newlines into <br> -- plain text would just collapse them."
  [text]
  (->> (str/split (or text "") #"\n")
       (interpose [:br])))

(defn- full-name [user]
  (when user (str (:firstname user) " " (:lastname user))))

(defn- returned-by [line]
  (let [name (str/trim
              (str (:returned_to_user_firstname line) " " (:returned_to_user_lastname line)))]
    (when-not (str/blank? name) name)))

(defn- contract-data [tx pool-id contract locale]
  (let [contract-id (:id contract)
        user (users/get-by-id tx (:user_id contract))
        delegated-user-id (reservations/contract-delegated-user-id tx contract-id)
        delegated-user (when delegated-user-id
                         (users/get-by-id tx delegated-user-id))
        handed-over-by-user-id (reservations/contract-handed-over-by-user-id tx contract-id)
        handed-over-by-user (when handed-over-by-user-id
                              (users/get-by-id tx handed-over-by-user-id))
        pool (inventory-pools/get-by-id tx pool-id)
        lines (reservations/get-with-details-for-contract tx contract-id pool-id)
        non-software-model-ids (->> lines
                                    (remove #(= "Software" (:model_type %)))
                                    (keep :model_id)
                                    distinct)
        accessories-by-model (when (seq non-software-model-ids)
                               (accessories/active-by-model-id tx non-software-model-ids pool-id))
        package-item-ids (->> lines
                              (filter #(and (:model_is_package %)
                                            (not= "Software" (:model_type %))))
                              (keep :item_id)
                              distinct)
        children-by-item (when (seq package-item-ids)
                           (items/children-by-parent-id tx package-item-ids))
        contact-details (orders/contact-details-for-contract tx contract-id)
        app-settings (settings tx [:local_currency_string
                                   :contract_lending_party_string
                                   :include_customer_email_in_contracts])]
    {:contract contract
     :user user
     :delegated-user delegated-user
     :handed-over-by-user handed-over-by-user
     :pool pool
     :returned-lines (filter :returned_date lines)
     :not-returned-lines (remove :returned_date lines)
     :accessories-by-model accessories-by-model
     :children-by-item children-by-item
     :contact-details contact-details
     :app-settings app-settings
     :total-price (->> lines (keep :price) (reduce + 0))
     :locale locale}))

;; -- rendering --------------------------------------------------------------

(defn- line-model-cell [line accessories-by-model children-by-item]
  [:td.model_name
   (:model_name line)
   (when (= "Software" (:model_type line))
     (list [:br] (:item_serial_number line)))
   (when (and (:model_is_package line) (not= "Software" (:model_type line)))
     [:ul (for [child (get children-by-item (:item_id line))]
            [:li (:model_name child) " " (:inventory_code child)])])
   (when (not= "Software" (:model_type line))
     (when-let [names (seq (map :name (get accessories-by-model (:model_id line))))]
       [:ul (for [name names] [:li name])]))])

(defn- lines-table [title lines accessories-by-model children-by-item locale]
  (when (seq lines)
    [:section.list
     [:h2 title]
     [:table
      [:thead
       [:tr
        [:td.quantity (t :contract.quantity locale)]
        [:td.inventory_code (t :contract.inventory-code locale)]
        [:td.model_name (t :contract.model locale)]
        [:td.end_date (t :contract.end-date locale)]
        [:td.returning_date (t :contract.return locale)]]]
      [:tbody
       (for [line (sort-by :model_name lines)]
         [:tr {:style "vertical-align: top"}
          [:td.quantity (:quantity line)]
          [:td.inventory_code (:item_inventory_code line)]
          (line-model-cell line accessories-by-model children-by-item)
          [:td.end_date (format-date (:end_date line))]
          [:td.returning_date
           (when (:returned_date line)
             (list (format-date (:returned_date line)) " " (returned-by line)))]])]]]))

(defn- customer-section [user delegated-user locale]
  (let [contact (or delegated-user user)]
    [:div.customer
     [:label (t :contract.borrower locale)]
     [:span.name (full-name user)]
     (when delegated-user (list [:br] [:span.name (full-name delegated-user)]))
     (when (:address contact) [:span.street (:address contact)])
     [:span.zip_city (str (:zip contact) " " (:city contact))]]))

(defn- inventory-pool-section [pool app-settings locale]
  [:div.inventory_pool
   [:label (t :contract.lender locale)]
   [:span.name (:name pool)]
   [:span (nl->br (:contract_lending_party_string app-settings))]])

(defn- contact-section [user delegated-user contact-details app-settings locale]
  (let [contact (or delegated-user user)
        emails (->> [(:email contact) (:secondary_email contact)]
                    (remove str/blank?))]
    (when (or (seq contact-details) (:include_customer_email_in_contracts app-settings))
      [:section.customer-contact {:style "margin-bottom: 0.5cm; margin-top: -0.5cm;"}
       [:label {:style "font-weight: bold"} (t :contract.contact-details locale)]
       (when (and (:include_customer_email_in_contracts app-settings) (seq emails))
         [:div.customer-email
          [:span.email {:style "display: block"} (str "E-Mail: " (str/join " / " emails))]])
       (when (seq contact-details)
         [:div.contact-details
          [:span.email {:style "display: block"} (str/join " / " contact-details)]])
       [:div.clear]])))

(defn- page [{:keys [contract user delegated-user handed-over-by-user pool
                     returned-lines not-returned-lines accessories-by-model
                     children-by-item contact-details app-settings total-price
                     locale]}]
  (str
   "<!DOCTYPE html>"
   (hiccup/html
    [:html
     [:head
      [:meta {:charset "utf-8"}]
      [:title (str "Lending Contract No. " (:compact_id contract) " | leihs")]
      [:link {:rel "stylesheet" :href "/lending/print/contract.css"}]]
     [:body
      [:div.contract
       [:div.barcode
        [:img {:src (barcode-data-uri (:compact_id contract))}]]
       [:h1
        (t :contract.title locale {:id (:compact_id contract)
                                   :date (format-date (:created_at contract))})
        [:span.weeknumber " W" (week-of-year (:created_at contract))]]
       [:div.date (format-date (time/today))]

       [:section.parties
        (customer-section user delegated-user locale)
        (inventory-pool-section pool app-settings locale)
        [:div.clear]]

       (contact-section user delegated-user contact-details app-settings locale)

       (lines-table (t :contract.returned-items locale) returned-lines
                    accessories-by-model children-by-item locale)
       (lines-table (t :contract.borrowed-items locale) not-returned-lines
                    accessories-by-model children-by-item locale)

       [:section.total-value
        [:p [:strong (t :contract.total-price locale)]]
        [:p (str (format-currency total-price) " "
                 (:local_currency_string app-settings))]]

       [:section.dontbreak
        [:section.purposes
         [:h2 (t :contract.purpose locale)]
         [:p (nl->br (:purpose contract))]]
        (when (:note contract)
          [:section.note
           [:h2 (t :contract.additional-notes locale)]
           [:p (nl->br (:note contract))]])
        [:section.terms_and_signature
         [:p.terms (t :contract.terms locale)]
         [:hr]
         [:span (t :contract.signature locale)]]]

       (when handed-over-by-user
         [:section [:p (t :contract.served-by locale {:name (full-name handed-over-by-user)})]])]]])))

(defn show [{:keys [tx pool-id authenticated-entity parameters]}]
  (let [contract-id (get-in parameters [:path :contract-id])
        contract (contracts-resource/get-one tx contract-id pool-id)]
    (cond
      (nil? contract)
      {:status 404 :headers {"Content-Type" "text/plain"} :body "Not Found"}

      (not (authorized? tx pool-id authenticated-entity contract))
      {:status 403 :headers {"Content-Type" "text/plain"} :body "Forbidden"}

      :else
      (let [locale (->> contract
                        :user_id
                        (lang/get-the-one-to-use tx)
                        :locale)]
        {:status 200
         :headers {"Content-Type" "text/html; charset=utf-8"}
         :body (page (contract-data tx pool-id contract locale))}))))
