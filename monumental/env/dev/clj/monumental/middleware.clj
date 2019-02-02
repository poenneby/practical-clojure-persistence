(ns monumental.middleware
    (:require [ring.middleware.content-type :refer [wrap-content-type]]
              [ring.middleware.params :refer [wrap-params]]
              [reitit.ring.coercion :as coercion]
              [reitit.ring.middleware.muuntaja :as muuntaja]
              [prone.middleware :refer [wrap-exceptions]]
              [ring.middleware.reload :refer [wrap-reload]]
              [ring.middleware.defaults :refer [site-defaults wrap-defaults]]))

(def middleware
  [#(wrap-defaults % site-defaults)
   wrap-exceptions
   wrap-reload
   muuntaja/format-middleware
   coercion/coerce-response-middleware
   coercion/coerce-request-middleware])
