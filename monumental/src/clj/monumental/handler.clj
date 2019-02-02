(ns monumental.handler
  (:require [reitit.ring :as reitit-ring]
            [monumental.middleware :refer [middleware]]
            [monumental.util :refer :all]
            [reitit.coercion.spec :as rcs]
            [muuntaja.core :as m]
            [clojure.java.io :as io]
            [hiccup.page :refer [include-js include-css html5]]
            [cheshire.core :refer [parse-string generate-string]]
            [config.core :refer [env]]))


(defonce monuments (parse-string (slurp (io/resource "firstHundred.json")) true))

(def mount-target
  [:div#app
   [:h1 "Monumental"]
   [:p "please wait while Figwheel is waking up ..."]
   [:p "(Check the js console for hints if nothing exсiting happens.)"]])

(defn head []
  [:head
   [:meta {:charset "utf-8"}]
   [:meta {:name "viewport"
           :content "width=device-width, initial-scale=1"}]
   (include-css (if (env :dev) "/css/site.css" "/css/site.min.css"))])

(defn loading-page []
  (html5
   (head)
   [:body {:class "body-container"}
    mount-target
    (include-js "/js/app.js")]))

(defn index-handler
  [_request]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body (loading-page)})


(defn monument-api-handler
 [{{{:keys [region]} :query} :parameters}]
                                  {:status 200
                                   :headers {"Content-Type" "application/json"}
                                   :body (generate-string (monuments-by-region monuments region))})

(def app
  (reitit-ring/ring-handler
   (reitit-ring/router
    [
     ["/" {:get {:handler index-handler}}]
     ["/api/search" {:get {:parameters {:query {:region string?}}
                       :handler monument-api-handler}}]]
    {:data {
            :muuntaja m/instance
            :coercion rcs/coercion
            :middleware middleware}})
   (reitit-ring/routes
    (reitit-ring/create-resource-handler {:path "/" :root "/public"})
    (reitit-ring/create-default-handler))))
