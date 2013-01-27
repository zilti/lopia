(ns lopia.forms.ticket
  (:use korma.incubator.core
        compojure.core))

(def crate-form
  )

(defroutes ticket
  (GET "/crate" []
       )
  (GET "/:id" [id :as r]))
