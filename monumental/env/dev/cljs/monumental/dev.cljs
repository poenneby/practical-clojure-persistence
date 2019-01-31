(ns ^:figwheel-no-load monumental.dev
  (:require
    [monumental.core :as core]
    [devtools.core :as devtools]))

(devtools/install!)

(enable-console-print!)

(core/init!)
