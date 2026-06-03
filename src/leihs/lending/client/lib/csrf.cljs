(ns leihs.lending.client.lib.csrf
  (:require [leihs.core.constants :as constants]
            [leihs.lending.client.lib.cookies :refer [get-cookie]]))

(def token
  (get-cookie constants/ANTI_CSRF_TOKEN_COOKIE_NAME))

(def cookie-name constants/ANTI_CSRF_TOKEN_COOKIE_NAME)

(def token-field-name constants/ANTI_CSRF_TOKEN_FORM_PARAM_NAME)

(def header-field-name constants/ANTI_CSRF_TOKEN_HEADER_NAME)
