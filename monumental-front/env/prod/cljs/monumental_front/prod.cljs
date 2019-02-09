(ns monumental-front.prod
  (:require [monumental-front.core :as core]))

;;ignore println statements in prod
(set! *print-fn* (fn [& _]))

(core/init!)
