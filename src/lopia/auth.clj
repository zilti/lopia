(ns lopia.auth "The authentication functions of Lopia use the [*\"Friend\"*](http://www.github.com/cemerick/friend) Framework from cemerick to ensure only LDAP-authorized users can access and, by group, modify the database data via the REST-API."
  (:require [clojure.string :as string]
            [lopia.util :as u]
            [clj-ldap.client :as ldap]
            [clj-time.core :as joda]
            [clj-time.coerce :as jodac]
            [cemerick.friend.credentials :as creds]))

(def ldap-conn "ldap-conn uses the data in resources/config/auth.clj :ldap-connection to connect to the LDAP-database."
  (ldap/connect (u/get ::ldap-connection)))

(defn get-user-data "All functions operating on user data use get-user-data to get the complete user data map of the given user. The argument used is the user's short login name."
  [sAMAccountName]
  (first
   (ldap/search ldap-conn (u/get ::ldap-base)
                {:filter (str "(sAMAccountName=" sAMAccountName ")")})))

(defn get-groups "A function returning a list of all groups of the given user associated to the short-name. To ensure compatibility with clojure keywords, **note that each space in a group name gets replaced by an underscore!**"
  [sAMAccountName]
  (->> (get-user-data sAMAccountName)
     :memberOf
     (map #(-> (split % #"\,")
              first
              (split #"=")
              second
              (string/replace " " "_")
              keyword))))

(defn workflow-ldap "Used by the friend authentication framework.
First checks if there's a valid token.
Otherwise, it tries to log in to ldap using the auth-ldap function.
If all fails, it returns nil."
  [{:keys [username password token] :as request}]
  (if-let [userdata (@sessions token)]
    (if (joda/after? (joda/now) (:timeout userdata))
      nil
      {:identity token
       :roles (get-groups username)})
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
          timeout (u/get ::session-timeout)]
      (swap! sessions
             #(assoc % pwhash
                     {:username username
                      :timeout (if (nil? timeout)
                                 nil
                                 (joda/plus (joda/now) (joda/secs timeout)))}))
      {:identity pwhash})
    nil))

(defn boot "Init function."
  []
  (u/load-file! "auth.clj"))
