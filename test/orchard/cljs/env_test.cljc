(ns orchard.cljs.env-test
  (:require [clojure.set :as set]
            [clojure.test :as test #?(:clj :refer :cljs :refer-macros) [deftest is testing use-fixtures]]
            [orchard.cljs.analysis :as a]
            [orchard.cljs.test-env :as test-env]))

(deftest test-env
  (let [env (test-env/create-test-env)]
    (testing "Test environment"
      (is (empty? (set/difference (set (keys (a/all-ns env)))
                                  '#{orchard.test-ns
                                     orchard.test-ns-dep
                                     cljs.core
                                     cljs.core.async
                                     cljs.core.async.impl.buffers
                                     cljs.core.async.impl.channels
                                     cljs.core.async.impl.dispatch
                                     cljs.core.async.impl.ioc-helpers
                                     cljs.core.async.impl.protocols
                                     cljs.core.async.impl.timers
                                     cljs.user
                                     clojure.set
                                     clojure.string
                                     mount.core
                                     mount.tools.logger
                                     mount.tools.macro}))))))
