(ns lopia.rest
  (:use compojure.core
        ring.middleware.params
        ring.middleware.nested-params
        ring.middleware.keyword-params
        ring.middleware.multipart-params)
  (:require [lopia.util :as u]
            [cemerick.friend :as friend]
            (cemerick.friend [workflows :as workflows]
                             [credentials :as creds])))

(defroutes lopia
  (GET "/" [] nil)
  (GET ["/t/:id", :id #"[0-9]+"] [id] nil)
  
  )

(def handler
  (-> lopia
     wrap-params
     wrap-multipart-params
     wrap-nested-params
     wrap-keyword-params
     (friend/authenticate {:credential-fn u/auth-ldap
                           :workflows [(u/workflow-ldap)]})))

(defn boot []
  (u/load-file! "routing.clj"))
