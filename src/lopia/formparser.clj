(ns lopia.formparser)

(defn field-matches? [field value]
  (fn [resmap]
    (= (field resmap) value)))

(defn field-not-null? [field]
  (fn [resmap]
    ((comp not nil) (field resmap))))

(def bestellung
  {:name "Bestellung"
   :fields {:bestellnummer :int
            :bestellstatus [:varchar ["Bestellungseingang"
                                      "Übernommen"
                                      "Bestellt"
                                      "Angekommen, wird bearbeitet"
                                      "Ausgeliefert"
                                      "Geschlossen"]]
            :bestellung :blob}
   :triggers [{:trigger :close
               :cond (field-matches? :bestellstatus "Geschlossen")}]
   :fulltext []})

(defn gen-hiccup [{:keys [name fields triggers fulltext] :as form}])
