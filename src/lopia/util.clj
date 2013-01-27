(ns lopia.util
  (:use flatland.ordered.map
        clojure.pprint)
  (:require [korma.incubator.core :as k]
            [cemerick.friend.credentials :as creds]))

;;*********************************************
;; Utility functions
;;*********************************************
(defn pspit
  "Pretty-spit."
  {:added "1.2"}
  [f content & options]
  (with-open [#^java.io.Writer w (apply clojure.java.io/writer f options)]
    (pprint content w)))

(defn safe-read [string]
  (binding [*read-eval* false]
    (read-string string)))
;;*********************************************
;; Settings
;;*********************************************
(def prefix "resources/conf/")
(def origin (atom {}))
(def settings (atom {}))

(defn parse-keyword
  "Parses a keyword and splits it into the namespace and keyword parts."
  [keyw]
  (let [[k1 k2] (string/split (subs (str keyw) 1) #"\/" 2)]
    [(keyword k1) (keyword k2)]))

(defn load-file!
  [filename]
  (let [f (->> (str prefix filename) slurp read-string)
        prefix (:prefix f)
        store (->> f (into (ordered-map)))]
    (swap! settings #(assoc % prefix store))
    (swap! origin #(assoc % prefix filename))))

(defn get
  [keyw]
  (let [[nspace keyw] (parse-keyword keyw)]
    (-> (get-in @settings [nspace keyw]) first)))

(defn put!
  [keyw value]
  (let [[nspace keyw] (parse-keyword keyw)]
    (swap! settings #(update-in % [nspace keyw]
                                (fn [old] `(~(first old) ~(second old) ~(last old)))))))

(add-watch settings :settings
           (fn [_ _ _ _]
             (for [entry @settings]
               (pspit (str prefix ((key entry) @origin)) (val entry)))))

;;*********************************************
;; Database utils
;;*********************************************
(defn lucene-search
  [search-str offset max]
  (let [res (k/exec-raw (str "SELECT * FROM FTL_SEARCH_DATA('" search-str "','" offset "','" max "')") :results)]
    (reduce (fn [vec rec]
              (conj vec [(-> rec :keys first) (:table rec)]))
            [] res)))

;;*********************************************
;; LDAP authorization
;;*********************************************
;; use creds to hash and check password hashes
(defn workflow-ldap [{:keys [username password token] :as request}]
  (if (nil? token)
    ))
(defn auth-ldap [reqmap])
