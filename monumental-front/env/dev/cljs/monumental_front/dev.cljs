(ns ^:figwheel-no-load monumental-front.dev
  (:require
    [monumental-front.core :as core]
    [devtools.core :as devtools]))

(devtools/install!)

(enable-console-print!)

(core/init!)
