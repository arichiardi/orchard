(ns orchard.test-ns
  (:refer-clojure :exclude [unchecked-byte while])
  (:require [clojure.core.async :refer [sliding-buffer] #?@(:cljs [:include-macros true])]
            [clojure.string]
            [mount.core :as mount #?@(:cljs [:include-macros true])]
            [orchard.test-ns-dep :as dep])
  #?(:cljs (:import [goog.ui IdGenerator])))

(defrecord TestRecord [a b c])
(deftype TestType [])

(def x ::some-namespaced-keyword)

(defn issue-28
  []
  (println "https://github.com/clojure-emacs/cljs-tooling/issues/28"))
