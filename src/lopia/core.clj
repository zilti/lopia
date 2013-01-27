(ns lopia.core
  (:require [korma.incubator.core :as k]
            [korma.db :as db]
            [clojure.string :as string]))

(db/defdb h2db {:classname "org.h2.Driver"
                :subprotocol "h2"
                :subname "mem:"
                :delimiters ""
                :naming {:keys string/lower-case
                         :fields string/lower-case}})

