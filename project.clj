(defproject cider/orchard "0.3.4"
  :description "A fertile ground for Clojure tooling"
  :url "https://github.com/clojure-emacs/orchard"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :scm {:name "git" :url "https://github.com/clojure-emacs/orchard"}
  :dependencies [;; We can't update dynapath to 1.0 as it removes the functionality we need from it
                 ;; We have to eventually apply the fix outlined here
                 ;; https://github.com/tobias/dynapath#note-on-urlclassloader
                 ;; See also https://github.com/clojure-emacs/cider-nrepl/issues/482
                 [org.tcrawley/dynapath "0.2.5"]
                 [org.clojure/java.classpath "0.3.0"]
                 [org.clojure/tools.namespace "0.3.0-alpha4"]]
  :exclusions [org.clojure/clojure] ; see versions matrix below

  :test-selectors {:java9 (complement :java9-excluded)}

  :aliases {"bump-version" ["change" "version" "leiningen.release/bump-version"]
            "test-watch" ["trampoline" "with-profile" "1.9,cljs,test" "test-refresh"]}

  :release-tasks [["vcs" "assert-committed"]
                  ["bump-version" "release"]
                  ["vcs" "commit" "Release %s"]
                  ["vcs" "tag" "v" "--no-sign"]
                  ["bump-version"]
                  ["vcs" "commit" "Begin %s"]]

  :deploy-repositories [["clojars" {:url "https://clojars.org/repo"
                                    :username :env/clojars_username
                                    :password :env/clojars_password
                                    :sign-releases false}]]

  :profiles {
             ;; Clojure versions matrix
             :provided {:dependencies [[org.clojure/clojure "1.10.0"]]}
             :1.8 {:dependencies [[org.clojure/clojure "1.8.0"]]}
             :1.9 {:dependencies [[org.clojure/clojure "1.9.0"]]}
             :1.10 {:dependencies [[org.clojure/clojure "1.10.0"]]}
             :master {:repositories [["snapshots"
                                      "https://oss.sonatype.org/content/repositories/snapshots"]]
                      :dependencies [[org.clojure/clojure "1.11.0-master-SNAPSHOT"]]}

             :cljs {:dependencies [[org.clojure/tools.reader "1.3.2"] ;; otherwise we have java.lang.IllegalAccessError: reader-error does not exist
                                   [org.clojure/clojurescript "1.10.439" :exclusions [org.clojure/tools.reader]]]
                    :global-vars {*assert* true}}

             :cljs-self-host {:resource-paths ["src" "test"]
                              :exclusions [org.clojure/clojure org.clojure/clojurescript]
                              :dependencies [[andare "0.9.0" :scope "test"]
                                             ;; mount is self-host compatible so better for testing
                                             [mount "0.1.15" :scope "test"]]
                              :global-vars {*assert* true}}

             :sysutils {:plugins [[lein-sysutils "0.2.0"]]}

             ;; DEV tools
             :test {:dependencies [[pjstadig/humane-test-output "0.9.0"]
                                   [org.clojure/core.async "0.4.474" :exclusions [org.clojure/tools.reader]]
                                   ;; mount is self-host compatible so choosen for testing
                                   [mount "0.1.15" :scope "test"]]
                    :resource-paths ["test-resources"]
                    :plugins [[com.jakemccrary/lein-test-refresh "0.23.0"]]
                    :injections [(require 'pjstadig.humane-test-output)
                                 (pjstadig.humane-test-output/activate!)]
                    :test-refresh {:changes-only true}}

             ;; CI tools
             :codox {:plugins [[lein-codox "0.10.3"]]
                     :codox #=(eval
                               (let [repo   (or (System/getenv "TRAVIS_REPO_SLUG") "clojure-emacs/orchard")
                                     branch (or (System/getenv "AUTODOC_SUBDIR") "master")
                                     urlfmt "https://github.com/%s/blob/%s/{filepath}#L{line}"]
                                 {;; Distinct docs for tagged releases as well as "master"
                                  :output-path (str "gh-pages/" branch)
                                  ;; Generate URI links from docs back to this branch in github
                                  :source-uri  (format urlfmt repo branch)}))}

             :cloverage {:plugins [[lein-cloverage "1.0.11-SNAPSHOT"]]}

             :cljfmt {:plugins [[lein-cljfmt "0.5.7"]]
                      :cljfmt {:indents {as-> [[:inner 0]]
                                         with-debug-bindings [[:inner 0]]
                                         merge-meta [[:inner 0]]}}}

             :eastwood {:plugins [[jonase/eastwood "0.3.4"]]
                        :eastwood {:config-files ["eastwood.clj"]}}})
