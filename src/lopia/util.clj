(ns lopia.util
  (:use flatland.ordered.map
        clojure.pprint)
  (:require [korma.incubator.core :as k]
            [clj-ldap.client :as ldap]
            [clj-time.core :as joda]
            [clj-time.coerce :as jodac]
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
(def sessions (atom {}))
(def ldap-conn (ldap/connect (get :core/ldap-connection)))

(defn get-user-data [sAMAccountName]
  (first
   (ldap/search ldap-conn (get :core/ldap-base)
                {:filter (str "(sAMAccountName=" sAMAccountName ")")})))

(defn get-groups [sAMAccountName]
  (->> (get-user-data sAMAccountName)
     :memberOf
     (map #(-> (split % #"\,")
              first
              (split #"=")
              second
              keyword))))

(defn workflow-ldap "Used by the friend authentication framework.
First checks if there's a valid token.
Otherwise, it tries to log in to ldap using the auth-ldap function.
If all fails, it returns nil."
  [{:keys [username password token] :as request}]
  (if-let [userdata (@sessions token)]
    (if (joda/after? (joda/now) (:timeout userdata))
      nil
      {:identity token})
    (if (and (not (nil? username)) (not (nil? password)))
      (auth-ldap request)
      nil)))

(defn auth-ldap "Used by the friend authentication framework.
Tries to log in to ldap using the given credentials in the request map."
  [{:keys [username password]}]
  (if (ldap/bind? ldap-conn
                  (-> (get-user-data username) first :dn)
                  password)
    (let [pwhash (creds/hash-bcrypt password)
          timeout (get :core/session-timeout)]
      (swap! sessions
             #(assoc % pwhash
                {:username username
                 :timeout (if (nil? timeout)
                            nil
                            (joda/plus (joda/now) (joda/secs timeout)))}))
      {:identity pwhash})
    nil))
;; TODO Authorization