(ns R
  (:refer-clojure :exclude [find-ns])
  (:require [clojure.test :as test]
            [clojure.repl :as repl]
            [kaocha.repl]
            clojure.pprint
            [clojure.tools.namespace.find :as ns.find]
            [clojure.tools.namespace.repl :as ns.repl]
            [clojure.java.io :as io])
  (:import (java.io File)))

(ns.repl/disable-reload! *ns*)

(defn help [& n]
  (println (str ">>> in ns " 'R))
  (mapv (fn [[k v]]
          (println v)) (ns-publics 'R))
  ::ok)

(defn init! []
  (ns.repl/disable-reload! *ns*)
  (ns.repl/set-refresh-dirs "src" "test")
  (help))

(def ^{:doc "Refresh changed namespaces"}  refresh ns.repl/refresh)
(def ^{:doc "Refresh everything"}  refresh-all ns.repl/refresh-all)
(def ^{:doc "Pretty pretint a thing"} pp clojure.pprint/pprint)

(defn list-ns
  "Return list of symbols of namespaces found in src dir"
  ([root]
   (ns.find/find-namespaces-in-dir (File. root)))
  ([]
   (list-ns "./src/")))

(defn find-ns
  "Find namespace vars by a regex"
  [re]
  (filter #(re-find re (str %)) (list-ns)))

(defn find-test-ns
  "Find test namespace vars by a regex"
  [re]
  (filter #(re-find re (str %)) (list-ns "./test/")))

(defn t
  "Run tests via kaocha - either all or a list of vars"
  ([]
   (kaocha.repl/run :unit))
  ([ns-list]
   (apply kaocha.repl/run ns-list)))

(defn t!
  "Run tests via kaocha, but refresh first - runs all tests or a list of vars"
  ([]
   (refresh)
   (kaocha.repl/run :unit))
  ([ns-list]
   (refresh)
   (apply kaocha.repl/run ns-list)))

(def system-status (atom {}))

(defn start-system!
  "Given a namespace, usually some-service, do the following:
  - find some-service.user namespace (by convention)
  - refresh
  - require the user ns e.g. some-service.user
  - start  system, invoking somer-service.user/start
  Warning: best if the system is not running, or things will go south

  Example: (R/start-system! 'foo.user)"
  ([]
   ;; automagically guess the <app>.user namespace
   (let [an-ns (-> *ns*
                   str
                   (clojure.string/replace #"\..+" ".user")
                   symbol)]
     (require an-ns)
     (start-system! an-ns)))
  ([an-ns]
  (do
    (printf "!! Starting %s\n" an-ns)
    (if (get @system-status an-ns)
      (println "!! System possibly running" an-ns)
      (do
        (println "!! Refreshing and reloading " an-ns)
        (remove-ns an-ns)
        (refresh)
        (require [an-ns] :reload)
        (if-let [f (ns-resolve an-ns 'start)]
          (do
            (f)
            (swap! system-status (fn [s] (assoc s an-ns true))))))))))

(defn stop-system!
  "Given a namespace, usually some-service.user, stop the system. If not passed, stops currently running system"
  ([]
   (stop-system! (first (keys @system-status))))
  ([an-ns]
  (let [f (ns-resolve an-ns 'stop)]
    (f)
    (swap! system-status (fn [s] (assoc s an-ns false))))))

(defn sys
  "Pull out the system for passing around"
  []
   (var-get (ns-resolve (first (keys @system-status)) 'SYS)))

(defn c
  "Pul out a compont from a running system"
  [component-name]
  (let [sys (sys)]
    (get sys component-name)))

(init!)
