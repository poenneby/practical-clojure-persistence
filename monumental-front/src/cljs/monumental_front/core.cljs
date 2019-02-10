(ns monumental-front.core
    (:require [reagent.core :as reagent :refer [atom]]
              [reagent.session :as session]
              [reitit.frontend :as reitit]
              [clerk.core :as clerk]
              [accountant.core :as accountant]
              [ajax.core :refer [GET]]
              [clojure.string :refer [lower-case]]
              ))

(defonce state (atom {:monuments []
                  :search ""}))

;; -------------------------
;; Routes

(def router
  (reitit/router
   [["/" :index]
    ["/monuments/:monument-id" :monument]]))

(defn path-for [route & [params]]
  (if params
    (:path (reitit/match-by-name router route params))
    (:path (reitit/match-by-name router route))))


(defn fetch-monuments [search]
  (swap! state assoc :search search)
  (GET "http://localhost:3000/api/search" {:params {:search search}
                                           :response-format :json
                                           :keywords? true
                                           :handler #(swap! state assoc :monuments %)}))

(defn monument-matching-ref [monuments ref]
  (filter (fn [monument] (= ref (:REF monument))) monuments))

(defn <search> []
  [:div [:input.searchInput
         {:placeholder "Search by region ..."
          :value (:search @state)
          :on-change #(fetch-monuments (-> % .-target .-value))}]])

(defn <monument-line> [monument]
  (let [current-name (:TICO monument)
        monument-ref (:REF monument)
        region (:REG monument)]
    [:li {:key monument-ref} current-name ", " region " "
     [:a {:href (path-for :monument {:monument-id monument-ref})} "more..."]]))

(defn <monuments> [] [:div.monuments
                      [:ul
                       (for [monument (:monuments @state)] (<monument-line> monument))]])

(defn <monument> [monument]
  (let [current-name (:TICO monument)
        details (:PPRO monument)
        department (:DPT monument)
        reference (:REF monument)]
    [:div
     [:h1 current-name]
     [:div details]
     [:div [:img {:src (str "https://monumentum.fr/photo/" department "/"  (lower-case (if (nil? reference) "not_found" reference)) ".jpg")}]]
     [:p [:a {:href (path-for :index)} "Back to the list of monuments"]]]))

;; -------------------------
;; Page components

(defn home-page []
  (fn []
    [:span.main
     [:h1 "Monumental"]
     [<search>]
     [<monuments>]]))

(defn monument-page []
  (if (empty? (:monuments @state)) (fetch-monuments "") nil)
  (fn []
    (let [routing-data (session/get :route)
          monument-id (get-in routing-data [:route-params :monument-id])
          monument (first (monument-matching-ref (:monuments @state) monument-id))
          ]
      [:span.main
        [<monument> monument]])))


;; -------------------------
;; Translate routes -> page components

(defn page-for [route]
  (case route
    :index #'home-page
    :monument #'monument-page))


;; -------------------------
;; Page mounting component

(defn current-page []
  (fn []
    (let [page (:current-page (session/get :route))]
      [:div
       [page]])))

;; -------------------------
;; Initialize app

(defn mount-root []
  (reagent/render [current-page] (.getElementById js/document "app")))

(defn init! []
  (clerk/initialize!)
  (accountant/configure-navigation!
   {:nav-handler
    (fn [path]
      (let [match (reitit/match-by-path router path)
            current-page (:name (:data  match))
            route-params (:path-params match)]
        (reagent/after-render clerk/after-render!)
        (session/put! :route {:current-page (page-for current-page)
                              :route-params route-params})
        (clerk/navigate-page! path)
        ))
    :path-exists?
    (fn [path]
      (boolean (reitit/match-by-path router path)))})
  (accountant/dispatch-current!)
  (mount-root))
