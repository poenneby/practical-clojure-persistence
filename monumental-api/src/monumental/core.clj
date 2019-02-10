(ns monumental.core
  (:require [clojure.string :as str]))

(defn monuments-by-region [monuments search]
  (filter (fn [m] (str/starts-with? (:REG m) search)) monuments))

