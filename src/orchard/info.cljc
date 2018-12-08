(ns orchard.info
  "Retrieve the info map from var and symbols."
  (:require
   #?@(:clj [[clojure.edn :as edn]
             [clojure.java.io :as io]
             [clojure.java.javadoc :as javadoc]
             [orchard.classloader :refer [class-loader]]
             [orchard.java :as java]])
   [clojure.spec.alpha :as s]
   [orchard.cljs.analysis :as cljs-ana #?@(:cljs [:include-macros true])]
   [orchard.cljs.meta :as cljs-meta #?@(:cljs [:include-macros true])]
   [orchard.meta :as m]
   [orchard.misc :as u]))

(defn normalize-ns-meta
  "Normalize cljs namespace metadata to look like a clj."
  [meta]
  (merge (select-keys meta [:doc :author])
         {:file (-> meta :defs first second :file)
          :line 1
          :name (:name meta)
          :ns (:name meta)}))

(defn qualify-sym
  "Qualify a symbol, if any in :sym, with :ns.

  Return nil if :sym is nil, attempting to generate a valid symbol even
  in case some :ns is missing."
  [ns sym]
  (when sym (symbol (some-> ns str) (str sym))))

(defn normalize-params
  "Normalize the info params.

  If :sym is unqualified we assoc a :qualified-sym key with it. The
  namespace used is :ns first and then :context-ns.

  If :sym is already qualified with assoc a :computed-ns key
  and :unqualified-sym key.

  If :dialect is nil, we assoc :clj, our default."
  [params]
  (let [{:keys [sym ns context-ns]} params]
    (cond-> (update params :dialect #(or % :clj))
      ;; If :sym is qualified, we have to use (name), cause:
      ;;   (namespace 'mount.core) ;;=> nil
      ;;   (name 'mount.core) ;;=> "mount.core
      (qualified-symbol? sym)
      (assoc :qualified-sym sym
             :unqualified-sym (u/name-sym sym)
             :computed-ns (u/namespace-sym sym))

      (not (qualified-symbol? sym))
      (assoc :unqualified-sym (-> sym name symbol))

      (and (not (qualified-symbol? sym)) (or ns context-ns))
      (assoc :qualified-sym (qualify-sym (or ns context-ns) sym)))))

(defn clj-meta
  [{:keys [dialect ns sym computed-ns unqualified-sym]}]
  {:pre [(= dialect :clj)]}
  (let [ns (or ns computed-ns)]
    (or
     ;; it's a special (special-symbol?)
     (m/special-sym-meta sym)
     ;; it's an unqualified sym for an aliased var
     (some-> ns (m/resolve-var unqualified-sym) (m/var-meta))
     ;; it's a var
     (some-> ns (m/resolve-var sym) (m/var-meta))
     ;; sym is an alias for another ns
     (some-> ns (m/resolve-aliases) (get sym) (m/ns-meta))
     ;; We use :unqualified-sym *exclusively* here because because our :ns is
     ;; too ambiguous.
     ;;
     ;; Observe the incorrect behavior (should return nil, there is a test):
     ;;
     ;;   (info '{:ns clojure.core :sym non-existing}) ;;=> {:author "Rich Hickey" :ns clojure.core ...}
     ;;
     (some-> (find-ns unqualified-sym) (m/ns-meta))
     ;; it's a Java class/member symbol...or nil
     (some-> ns (java/resolve-symbol sym)))))

(defn cljs-meta
  [{:keys [dialect ns sym env context-ns unqualified-sym]}]
  {:pre [(= dialect :cljs)]}
  (let [context-ns (or context-ns ns)]
    (or
     ;; a special symbol - always use :unqualified-sym
     (some-> (cljs-ana/special-meta env unqualified-sym)
             (cljs-meta/normalize-var-meta))
     ;; an NS
     (some->> (cljs-ana/find-ns env sym)
              (normalize-ns-meta))
     ;; ns alias
     (some->> (cljs-ana/ns-alias env sym context-ns)
              (cljs-ana/find-ns env)
              (normalize-ns-meta))
     ;; macro ns
     #?(:clj (some->> (find-ns sym)
                      (cljs-meta/normalize-macro-ns env))
        :cljs (some->> (u/add-ns-macros sym)
                       (cljs-ana/find-ns env)
                       (cljs-meta/normalize-macro-ns env)))
     ;; macro ns alias
     (some->> (cljs-meta/aliased-macro-var env sym context-ns)
              (cljs-meta/normalize-macro-ns env))
     ;; referred var
     (some->> (get (cljs-ana/referred-vars env context-ns) sym)
              (cljs-ana/find-symbol-meta env)
              (cljs-meta/normalize-var-meta))
     ;; referred macro
     (some->> (cljs-meta/referred-macro-meta env sym context-ns)
              (cljs-meta/normalize-macro-meta))
     ;; scoped var
     (some->> (cljs-meta/scoped-var-meta env sym context-ns)
              (cljs-meta/normalize-var-meta))
     ;; scoped macro
     (some->> (cljs-meta/scoped-macro-meta env sym context-ns)
              (cljs-meta/normalize-macro-meta))
     ;; var in cljs.core
     (some->> (get (cljs-ana/core-vars env context-ns) sym)
              (cljs-ana/var-meta)
              (cljs-meta/normalize-var-meta))
     ;; macro in cljs.core
     (some->> (cljs-meta/scoped-macro-meta env sym 'cljs.core)
              (cljs-meta/normalize-macro-meta)))))

(defn info
  [params]
  {:pre [(contains? #{:cljs :clj nil} (:dialect params))]}
  (let [params (normalize-params params)
        dialect (:dialect params)]
    (cond-> params
      ;; TODO split up responsability of finding meta and normalizing the meta map
      (= dialect :clj) clj-meta
      (= dialect :cljs) cljs-meta)))

#?(:clj
   (def see-also-data
     (edn/read-string (slurp (io/resource "see-also.edn")))))

#?(:clj
   (defn see-also
     [ns sym]
     (let [var-key (str ns "/" sym)]
       (->> (get see-also-data var-key)
            (filter (comp resolve u/as-sym))))))

#?(:clj
   (defn info-java
     [class member]
     (java/member-info class member)))

#?(:clj
   (defn- resource-full-path [relative-path]
     (io/resource relative-path (class-loader))))

#?(:clj
   (defn resource-path
     "If it's a resource, return a tuple of the relative path and the full resource path."
     [x]
     (or (if-let [full (resource-full-path x)]
           [x full])
         (if-let [[_ relative] (re-find #".*jar!/(.*)" x)]
           (if-let [full (resource-full-path relative)]
             [relative full]))
         ;; handles load-file on jar resources from a cider buffer
         (if-let [[_ relative] (re-find #".*jar:(.*)" x)]
           (if-let [full (resource-full-path relative)]
             [relative full])))))

#?(:clj
   (defn file-path
     "For a file path, return a URL to the file if it exists and does not
     represent a form evaluated at the REPL."
     [x]
     (when (seq x)
       (let [f (io/file x)]
         (when (and (.exists f)
                    (not (-> f .getName (.startsWith "form-init"))))
           (io/as-url f))))))

#?(:clj
   (defn file-info
     [path]
     (let [[resource-relative resource-full] (resource-path path)]
       (merge {:file (or (file-path path) resource-full path)}
              ;; Classpath-relative path if possible
              (if resource-relative
                {:resource resource-relative})))))

#?(:clj
   (defn javadoc-info
     "Resolve a relative javadoc path to a URL and return as a map. Prefer javadoc
     resources on the classpath; then use online javadoc content for core API
     classes. If no source is available, return the relative path as is."
     [^String path]
     {:javadoc
      (or (resource-full-path path)
          ;; [bug#308] `*remote-javadocs*` is outdated WRT Java
          ;; 8, so we try our own thing first.
          (when (re-find #"^(java|javax|org.omg|org.w3c.dom|org.xml.sax)/" path)
            (format "http://docs.oracle.com/javase/%d/docs/api/%s"
                    u/java-api-version path))
          ;; If that didn't work, _then_ we fallback on `*remote-javadocs*`.
          (some (let [classname (.replaceAll path "/" ".")]
                  (fn [[prefix url]]
                    (when (.startsWith classname prefix)
                      (str url path))))
                @javadoc/*remote-javadocs*)
          path)}))

;; TODO: Seems those were hardcoded here accidentally - we should
;; probably provide a simple API to register remote JavaDocs.
#?(:clj
   (javadoc/add-remote-javadoc "com.amazonaws." "http://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/"))

#?(:clj
   (javadoc/add-remote-javadoc "org.apache.kafka." "https://kafka.apache.org/090/javadoc/index.html?"))
