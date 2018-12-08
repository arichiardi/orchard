(ns orchard.info-test
  (:require
   [clojure.test :as test #?(:clj :refer :cljs :refer-macros) [deftest is testing use-fixtures]]
   [clojure.string :as str]
   [orchard.info :as info]
   [orchard.misc :as misc]
   [orchard.cljs.test-env :as test-env]
   #?@(:clj [[clojure.repl :as repl]
             [orchard.test-ns]])))

(def ^:dynamic *cljs-params*)

(defn wrap-info-params
  [f]
  (binding [*cljs-params* {:dialect :cljs
                           :env (test-env/create-test-env)}]
    (f)))

(use-fixtures :once wrap-info-params)

;; TODO use test-ns instead
;; (is (info/info 'orchard.info-test 'TestType))
;; (is (info/info 'orchard.info-test 'TestRecord))

(deftest info-non-existing-test
  (is (nil? (info/info {:ns 'clojure.core :sym (gensym "non-existing")}))))

(deftest info-special-form-test
  (testing "special forms are marked as such and nothing else is (for all syms in ns)"
    (let [target-ns 'orchard.info
          ns-syms (keys (ns-map target-ns))
          info-specials (fn [info-params]
                          (as-> (map info/info info-params) it
                            (group-by :special-form it)
                            (get it true)
                            (map :name it)
                            (set it)))]
      (testing "- :cljs"
        (let [special-syms (into '#{in-ns load load-file} #?(:clj (keys @#'cljs.repl/special-doc-map)))]
          (is (= special-syms (->> (into special-syms ns-syms)
                                   (map #(merge *cljs-params* {:ns target-ns :sym %}))
                                   (info-specials))))))
      (testing "- :clj"
        (let [special-syms (into '#{letfn let loop fn} (keys @#'clojure.repl/special-doc-map))]
          (is (= special-syms (->> (into special-syms ns-syms)
                                   (map #(hash-map :ns target-ns :sym %))
                                   (info-specials)))))))))

(deftest info-var-alias-test
  #(testing "Aliased var"
     (let [params '{:ns 'cljs.core.async
                    :sym 'dispatch/process-messages}
           expected '{:ns cljs.core.async.impl.dispatch
                      :name process-messages
                      :arglists ([])
                      :line 13}]
       (testing "- :cljs"
         (let [i (info/info (merge *cljs-params* params))]
           (is (= expected (select-keys i [:ns :name :arglists :doc])))
           (is (str/includes? (:file i) "cljs/core/async/impl/dispatch.cljs"))))
       (testing "- :clj"
         (let [i (info/info params)]
           (is (= expected (select-keys i [:ns :name :arglists :doc])))
           (is (str/includes? (:file i) "cljs/core/async/impl/dispatch.cljs")))))))

(deftest info-fully-qualified-var-test
  (testing "Fully-qualified var"
    (let [params '{:ns orchard.test-ns
                   :sym clojure.string/trim}
          expected '{:ns clojure.string
                     :name trim
                     :arglists ([s])
                     :doc "Removes whitespace from both ends of string."}]
      (testing "- :cljs"
        (is (= expected (-> (info/info (merge *cljs-params* params))
                            (select-keys [:ns :name :arglists :doc])))))
      (testing "- :clj"
        (is (= expected (-> (info/info params)
                            (select-keys [:ns :name :arglists :doc])
                            (update :ns ns-name))))))))

(deftest info-unqualified-sym-and-namespace-test
  (testing "Resolution from current namespace"
    (let [params '{:ns cljs.core
                   :sym +}]
      (testing "- :cljs"
        (let [i (info/info (merge *cljs-params* params))]
          (is (= '+ (:name i)))
          (is (= 'cljs.core (:ns i)))))
      (testing "- :clj"
        (let [i (info/info params)]
          (is (= '+ (:name i)))
          (is (= 'cljs.core (:ns i))))))))

(deftest info-cljs-tooling-issue-28-test
  (testing "Resolution from current namespace - issue #28 from cljs-tooling"
    (let [i (info/info (merge *cljs-params*
                              '{:ns orchard.test-ns :sym issue-28}))]
      (is (= '{:arglists ([])
               :line 14
               :column 1
               :ns orchard.test-ns
               :name issue-28}
             (select-keys i [:arglists :line :column :ns :name])))
      (is (str/includes? (:file i) "orchard/test_ns")))))

(deftest info-unqualified-sym-required-namespace-test
  (testing "Resolution from other namespaces"
    (let [params '{:ns cljs.core.async
                   :sym +}]
      (testing "- :cljs"
        (let [i (info/info (merge *cljs-params* params))]
          (is (= (-> #'+ meta :name) (:name i)))
          (is (= 'cljs.core (:ns i)))))
      (testing "- :clj"
        (let [i (info/info params)]
          (is (= '+ (:name i)))
          (is (= 'clojure.core (:ns i))))))))

(deftest info-ns-as-sym-test
  (testing "Only namespace as qualified symbol "
    (let [params '{:sym orchard.test-ns}
          expected '{:ns orchard.test-ns
                     :name orchard.test-ns
                     :line 1}]
      (testing "- :cljs"
        (let [i (info/info (merge *cljs-params* params))]
          (is (= expected (select-keys i [:line :name :ns])))
          (is (str/includes? (:file i) "orchard/test_ns"))))
      (testing "- :clj"
        (let [i (info/info params)]
          (is (= expected (select-keys i [:line :doc :name :ns])))
          (is (str/includes? (:file i) "orchard/test_ns"))))
      ;; is how the info middleware sends it
      (testing "- :cljs with context"
        (let [params '{:context-ns orchard.test-ns
                       :sym orchard.test-ns}
              i (info/info (merge *cljs-params* params))]
          (is (= '{:ns orchard.test-ns
                   :name orchard.test-ns
                   :line 1}
                 (select-keys i [:line :name :ns])))
          (is (str/includes? (:file i) "orchard/test_ns")))))))

(deftest info-ns-dependency-as-sym-test
  (testing "Namespace dependency"
    (let [params '{:sym orchard.test-ns-dep}
          expected '{:ns orchard.test-ns-dep
                     :name orchard.test-ns-dep
                     :line 1}]
      (testing "- :cljs"
        (let [i (info/info (merge *cljs-params* params))]
          (is (= expected (select-keys i [:line :name :ns])))
          (is (str/includes? (:file i) "orchard/test_ns_dep"))))
      (testing "- :clj"
        (let [i (info/info params)]
          (is (= expected (select-keys i [:line :doc :name :ns])))
          (is (str/includes? (:file i) "orchard/test_ns_dep"))))
      ;; is how the info middleware sends it
      (testing "- :cljs with context"
        (let [params '{:sym orchard.test-ns-dep
                       :context-ns orchard.test-ns}
              i (info/info (merge *cljs-params* params))]
          (is (= '{:ns orchard.test-ns-dep
                   :name orchard.test-ns-dep
                   :line 1}
                 (select-keys i [:line :name :ns])))
          (is (str/includes? (:file i) "orchard/test_ns_dep")))))))

(deftest info-cljs-core-namespace-test
  (testing "Namespace itself but cljs.core"
    (testing "- :cljs"
      (is (= 'cljs.core (:ns (info/info (merge *cljs-params* '{:sym cljs.core}))))))
    (testing "- :clj"
      (is (= 'clojure.core (:ns (info/info '{:sym clojure.core})))))))

(deftest info-namespace-alias-test
  (testing "Namespace alias"
    (testing "- :cljs"
      (let [params '{:ns cljs.core.async
                     :sym dispatch}
            expected '{:ns cljs.core.async.impl.dispatch
                       :name cljs.core.async.impl.dispatch
                       :line 1}
            i (info/info (merge *cljs-params* params))]
        (is (= expected (select-keys i [:ns :name :arglists :line])))
        (is (str/includes? (:file i) "core/async/impl/dispatch"))))

    (testing "- :clj"
      (let [params '{:ns clojure.core.async
                     :sym dispatch}
            expected '{:ns clojure.core.async.impl.dispatch
                       :name clojure.core.async.impl.dispatch
                       :line 1}
            i (info/info params)]
        (is (= expected (select-keys i [:ns :name :arglists :line])))
        (is (str/includes? (:file i) "core/async/impl/dispatch"))))))

(deftest info-namespace-macro-test
  (testing "Macro namespace"
    (testing "- :cljs"
      (let [params '[{:sym cljs.core.async.impl.ioc-macros}
                     {:sym cljs.core.async.impl.ioc-macros
                      :ns cljs.core.async}
                     {:sym cljs.core.async.impl.ioc-macros
                      :context-ns cljs.core.async}
                     {:sym cljs.core.async.impl.ioc-macros
                      :context-ns orchard.test-ns}]
            expected '{:ns cljs.core.async.impl.ioc-macros
                       :file "cljs/core/async/impl/ioc_macros.clj"
                       :name cljs.core.async.impl.ioc-macros
                       :line 1}]
        (is (= (take 4 (repeat expected))
               (map #(info/info (merge *cljs-params* %)) params)))))))


(deftest info-namespace-cljs-core-macro-test
  (testing "cljs.core macro"
    (testing "- :cljs"
      (let [params '[{:sym loop}
                     {:sym loop :ns cljs.core}
                     {:sym loop :context-ns cljs.core}
                     {:sym cljs.core/loop}
                     {:sym cljs.core/loop :context-ns cljs.core.async}
                     {:sym cljs.core/loop :ns cljs.core.async}]
            expected '{:ns cljs.core
                       :doc "Evaluates the exprs in a lexical context in which the symbols in\n  the binding-forms are bound to their respective init-exprs or parts\n  therein. Acts as a recur target."
                       :name loop
                       :arglists ([bindings & body])}]
        (is (= (take 6 (repeat expected))
               (->> params
                    (map #(info/info (merge *cljs-params* %)))
                    (map #(select-keys % [:ns :name :doc :arglists])))))))))

(deftest info-namespace-macro-alias-test
  (testing "Macro namespace alias"
    (testing "- :cljs"
      (let [params '[{:sym ioc :context-ns cljs.core.async}
                     {:sym ioc :ns cljs.core.async}]
            expected '{:ns cljs.core.async.impl.ioc-macros
                       :name cljs.core.async.impl.ioc-macros
                       :file "cljs/core/async/impl/ioc_macros.clj"
                       :line 1}]
        (is (= (take 2 (repeat expected))
               (map #(info/info (merge *cljs-params* %)) params)))))))


(deftest info-macros-var-test
  (testing "Macro"
    (testing "- :cljs"
      (let [params '[{:sym cljs.core.async/go}
                     {:ns cljs.core.async
                      :sym go}]
            expected '{:ns cljs.core.async
                       :name go
                       :arglists ([& body])
                       :macro true
                       :file "cljs/core/async.clj"}]
        (is (= (take 2 (repeat expected))
               (->> params
                    (map #(info/info (merge *cljs-params* %)))
                    (map #(select-keys % [:ns :name :arglists :macro :file])))))))

    (testing "- :clj"
      (let [params '[{:sym clojure.core.async/go}
                     {:ns clojure.core.async
                      :sym go}]
            expected '{:ns clojure.core.async
                       :name go
                       :arglists ([& body])
                       :macro true
                       :file "clojure/core/async.clj"}]
        (is (= (take 2 (repeat expected))
               (->> params
                    (map #(info/info %))
                    (map #(select-keys % [:ns :name :arglists :macro :file])))))))))

(deftest info-macros-referred-var-test
  (testing "Macro - referred"
    (let [params '[{:sym mount.core/on-error}
                   {:ns mount.core
                    :sym on-error}]
          expected '{:name on-error
                     :ns mount.tools.macro
                     :arglists ([msg f & {:keys [fail?] :or {fail? true}}])
                     :file "mount/tools/macro.cljc"
                     :macro true}]

      (testing "- :cljs"
        (is (= (take 2 (repeat expected))
               (->> params
                    (map #(info/info (merge *cljs-params* %)))
                    (map #(select-keys % [:ns :name :arglists :macro :file])) ))))

      (testing "- :clj"
        (is (= (take 2 (repeat expected))
               (->> params
                    (map #(info/info %))
                    (map #(select-keys % [:ns :name :arglists :macro :file])))))))))

;;;;;;;;;;;;;;;;;;
;; Clojure Only ;;
;;;;;;;;;;;;;;;;;;

#?(:clj
   (deftest see-also-test
     (is (not-empty (info/see-also 'clojure.core 'map)))))

#?(:clj
   (deftest info-jvm-test
     (is (info/info {:ns 'orchard.info :sym 'java.lang.Class}))
     (is (info/info {:ns 'orchard.info :sym 'Class/forName}))
     (is (info/info {:ns 'orchard.info :sym '.toString}))))

#?(:clj
   (deftest info-java-test
     (is (info/info-java 'clojure.lang.Atom 'swap))))

#?(:clj
   (deftest javadoc-info-unit-test
     (testing "Get an HTTP URL for a Sun/Oracle Javadoc"
       (testing "Javadoc 1.7 format"
         (let [reply      (info/javadoc-info "java/lang/StringBuilder.html#capacity()")
               url        (:javadoc reply)
               exp-suffix "/docs/api/java/lang/StringBuilder.html#capacity()"]
           (is (str/includes? url exp-suffix))))

       (testing "Javadoc 1.8 format"
         (let [reply      (info/javadoc-info "java/lang/StringBuilder.html#capacity--")
               url        (:javadoc reply)
               exp-suffix "/docs/api/java/lang/StringBuilder.html#capacity--"]
           (is (str/includes? url exp-suffix)))))

     (testing "Get general URL for a clojure javadoc"
       (let [reply    (info/javadoc-info "clojure/java/io.clj")
             url      (:javadoc reply)
             url-type (class url)
             exp-type java.net.URL]
         (is (= url-type exp-type))))

     (testing "Get URL for commonly used Java libraries via the *remote-javadocs* mechanism"
       (let [reply    (info/javadoc-info "com/amazonaws/services/lambda/AWSLambdaClient.html#listFunctions()")
             url      (:javadoc reply)]
         (is (= url "http://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/services/lambda/AWSLambdaClient.html#listFunctions()"))))

     (testing "Get fall through URL type for other Javadocs (external libs?)"
       (let [reply (info/javadoc-info "http://some/other/url")
             url (:javadoc reply)]
         (is (= url "http://some/other/url"))))))

;; TODO: Assess the value of this test
#?(:clj
   (deftest javadoc-url-test
     (if (= misc/java-api-version 7)
       (testing "java 1.7"
         (is (= "java/lang/StringBuilder.html#charAt(int)"
                (-> (info/info-java 'java.lang.StringBuilder 'charAt)
                    (get :javadoc))))))

     (if (= misc/java-api-version 8)
       (testing "java 1.8"
         (is (= "java/lang/StringBuilder.html#charAt-int-"
                (-> (info/info-java 'java.lang.StringBuilder 'charAt)
                    (get :javadoc))))))

     (if (= misc/java-api-version 9)
       (testing "java 9"
         (is (= "java/lang/StringBuilder.html#charAt-int-"
                (-> (info/info-java 'java.lang.StringBuilder 'charAt)
                    (get :javadoc))))))))

;;; resource path test
#?(:clj
   (defn file
     [x]
     (:file (info/file-info x))))

#?(:clj
   (defn relative
     [x]
     (:resource (info/file-info x))))

#?(:clj
   (deftest resource-path-test
     (is (= (class (file (subs (str (clojure.java.io/resource "clojure/core.clj")) 4)))
            java.net.URL))
     (is (= (class (file "clojure/core.clj"))
            java.net.URL))
     (is (= (class (file "clojure-1.7.0.jar:clojure/core.clj"))
            java.net.URL))
     (is (= (class (file "test/orchard/info_test.cljc"))
            java.net.URL))
     (is (relative "clojure/core.clj"))
     (is (nil? (relative "notclojure/core.clj")))
     (is (nil? (info/resource-path "jar:file:fake.jar!/fake/file.clj")))))

#?(:clj
   ;; TODO: What's the value of this test?
   (deftest boot-resource-path-test
     (let [tmp-dir-name (System/getProperty "java.io.tmpdir")
           tmp-file-name "boot-test.txt"
           tmp-file-path (str tmp-dir-name (System/getProperty "file.separator") tmp-file-name)]
       (spit tmp-file-path "test")
       (testing "when fake.class.path is not set"
         (is (not (= (class (file tmp-file-name))
                     java.net.URL)))
         (is (= (file tmp-file-name) tmp-file-name)))
       (testing "when fake.class.path is set"
         (try
           (System/setProperty "fake.class.path" tmp-dir-name)
           (is (= (class (file tmp-file-name))
                  java.net.URL))
           (is (= (.getPath (file tmp-file-name))
                  tmp-file-path))
           (finally
             (System/clearProperty "fake.class.path")))))))

(deftest qualify-sym-test
  (is (= '+ (info/qualify-sym nil '+)))
  (is (nil? (info/qualify-sym 'cljs.core nil)))
  (is (nil? (info/qualify-sym  nil nil)))
  (is (= 'cljs.core/+ (info/qualify-sym 'cljs.core '+))))

(deftest normalize-params-test
  (testing ":qualified-sym namespace coming from :ns"
    (is (= 'cljs.core/+ (-> '{:ns cljs.core
                              :sym +
                              :context-ns orchard.info}
                            info/normalize-params
                            :qualified-sym))))

  (testing ":qualified-sym namespace coming from :context-ns if :ns is missing"
    (is (= 'orchard.info/+ (-> '{:sym + :context-ns orchard.info}
                               info/normalize-params
                               :qualified-sym))))

  (testing "adding :qualified-sym if :sym is qualified"
    (is (= '{:sym orchard.info/+
             :qualified-sym orchard.info/+}
           (-> '{:sym orchard.info/+}
               (info/normalize-params)
               (select-keys [:sym :qualified-sym])))))

  (testing "adding :computed-ns if :sym is qualified"
    (is (= '{:sym orchard.info/+
             :computed-ns orchard.info}
           (-> '{:sym orchard.info/+}
               (info/normalize-params)
               (select-keys [:sym :computed-ns])))))

  (testing "adding :unqualified-sym if :sym is qualified"
    (is (= '{:sym orchard.info/+
             :unqualified-sym +}
           (-> '{:sym orchard.info/+}
               (info/normalize-params)
               (select-keys [:sym :unqualified-sym])))))

  (testing "adding :unqualified-sym if :sym is unqualified"
    (is (= '{:sym +
             :unqualified-sym +}
           (-> '{:sym +}
               (info/normalize-params)
               (select-keys [:sym :unqualified-sym]))))))
