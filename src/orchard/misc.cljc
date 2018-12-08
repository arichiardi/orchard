(ns orchard.misc
  (:require
   [clojure.string :as str]))

#?(:clj
   (defn os-windows? []
     (.startsWith (System/getProperty "os.name") "Windows")))

#?(:clj
   (defn boot-fake-classpath
     "Retrieve Boot's fake classpath.
     When using Boot, fake.class.path contains the original directories with source
     files, which makes it way more useful than the real classpath.
     See https://github.com/boot-clj/boot/issues/249 for details."
     []
     (System/getProperty "fake.class.path")))

#?(:clj
   (defn boot-project?
     "Check whether we're dealing with a Boot project.
     We figure this by checking for the presence of Boot's fake classpath."
     []
     (not (nil? (boot-fake-classpath)))))

(defn as-sym
  [x]
  (cond
    (symbol? x) x
    (string? x) (if-let [[_ ns sym] (re-matches #"(.+)/(.+)" x)]
                  (symbol ns sym)
                  (symbol x))))

(defn update-vals
  "Update the values of map `m` via the function `f`."
  [f m]
  (reduce (fn [acc [k v]]
            (assoc acc k (f v)))
          {} m))

(defn update-keys
  "Update the keys of map `m` via the function `f`."
  [f m]
  (reduce (fn [acc [k v]]
            (assoc acc (f k) v))
          {} m))

(defn deep-merge
  "Merge maps recursively. When vals are not maps, last value wins."
  [& xs]
  (let [f (fn f [& xs]
            (if (every? map? xs)
              (apply merge-with f xs)
              (last xs)))]
    (apply f (filter identity xs))))

#?(:clj
   (def java-api-version
     (try
       (let [java-ver (System/getProperty "java.version")
             [major minor _] (str/split java-ver #"\.")
             major (Integer/parseInt major)
             minor (Integer/parseInt minor)]
         (if (> major 1)
           major
           (or minor 7)))
       (catch Exception _ 7))))

(defmulti transform-value "Transform a value for output" type)

(defmethod transform-value :default [v] (str v))

(defmethod transform-value nil [v] nil)

#?(:clj
   (defmethod transform-value Number [v] v))

#?(:clj
   (defmethod transform-value java.io.File
     [v]
     (.getAbsolutePath ^java.io.File v)))

#?(:clj
   (defmethod transform-value clojure.lang.Sequential
     [v]
     (list* (map transform-value v))))

#?(:clj
   (defmethod transform-value clojure.lang.Symbol
     [v]
     (let [[the-ns the-name] [(namespace v) (name v)]]
       (if the-ns
         (str the-ns "/" the-name)
         the-name))))

#?(:clj
   (defmethod transform-value clojure.lang.Keyword
     [v]
     (transform-value (.sym ^clojure.lang.Keyword v))))

#?(:clj
   (defmethod transform-value clojure.lang.Associative
     [m]
     (->> (for [[k v] m] ; bencode keys must be strings
            [(str (transform-value k)) (transform-value v)])
          (into {}))))

;; handles vectors
#?(:clj
   (prefer-method transform-value clojure.lang.Sequential clojure.lang.Associative))

;; from https://github.com/flatland/useful/blob/develop/src/flatland/useful/experimental.clj#L31
(defmacro cond-let
  "An implementation of cond-let that is as similar as possible to if-let. Takes multiple
  test-binding/then-form pairs and evalutes the form if the binding is true. Also supports
  :else in the place of test-binding and always evaluates the form in that case.

  Example:
   (cond-let [b (bar 1 2 3)] (println :bar b)
             [f (foo 3 4 5)] (println :foo f)
             [b (baz 6 7 8)] (println :baz b)
             :else           (println :no-luck))"
  [test-binding then-form & more]
  (let [test-binding (if (= :else test-binding) `[t# true] test-binding)
        else-form    (when (seq more) `(cond-let ~@more))]
    `(if-let ~test-binding
       ~then-form
       ~else-form)))

(defn namespace-sym
  "Return the namespace of a fully qualified symbol if possible.

  It leaves the symbol untouched if not."
  [sym]
  (if-let [ns (and sym (namespace sym))]
    (as-sym ns)
    sym))

(defn name-sym
  "Return the name of a fully qualified symbol if possible.

  It leaves the symbol untouched if not."
  [sym]
  (if-let [n (and sym (name sym))]
    (as-sym n)
    sym))

(defn add-ns-macros
  "Append $macros to the input symbol"
  [sym]
  (some-> sym
          (str "$macros")
          symbol))

(defn remove-macros
  "Remove $macros from the input symbol"
  [sym]
  (some-> sym
          str
          (str/replace #"\$macros" "")
          symbol))

(defn ns-obj?
  "Return true if n is a namespace object"
  [ns]
  (instance? #?(:clj clojure.lang.Namespace
                :cljs cljs.core/Namespace)
             ns))
