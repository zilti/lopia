(ns lopia.util "Lopia.Util contains utility functions which aren't related enough to other functions to get their own namespace."
    (:use flatland.ordered.map
        clojure.pprint)
  (:require [korma.incubator.core :as k]))

;; ## Utility functions
;; Utility functions are globally used functions that complement clojure.core
;; in the context of Lopia.  
(defn pspit
  "A \"pretty-spit\" function combining clojure.core's spit with pprint."
  [f content & options]
  (with-open [#^java.io.Writer w (apply clojure.java.io/writer f options)]
    (pprint content w)))

(defn safe-read "Used to read input from unsafe sources like the browser.
The binding of *read-eval* to false prevents execution of code using the #= reader literal."
  [string]
  (binding [*read-eval* false]
    (read-string string)))

;; ## Settings
;; Lopia settings are organized in a namespaced manner.  
;; each Lopia settings file contains exactly one clojure map. The usage of flatland's ordered map ensures that the order of the settings is kept, so it stays simple to edit them by hand.  
;; Each of those maps needs a :prefix entry whose value is a keyword denoting the namespace with which the contents can be accessed. Usually that namespace is identical to the one of the namespace using the settings file.  
;; All other entries have to follow the following pattern:  
;; ```{:keyword [value docstring default-value]}```  
;; This ensures that we have self-documenting configuration files.  
(def prefix "This prefix is where all config files are stored." "resources/conf/")
(def origin "Origin stores the origin of the setting namespaces, so we still know where to store the different parts of the settings map." (atom {}))
(def settings "This is the main collection for all settings." (atom {}))

(defn parse-keyword
  "Parses a keyword and splits it into the namespace and keyword parts."
  [keyw]
  (let [[k1 k2] (string/split (subs (str keyw) 1) #"\/" 2)]
    [(keyword k1) (keyword k2)]))

(defn load-file! "Loads a configuration file and loads it into the settings-atom."
  [filename]
  (let [f (->> (str prefix filename) slurp read-string)
        prefix (:prefix f)
        store (->> f (into (ordered-map)))]
    (swap! settings #(assoc % prefix store))
    (swap! origin #(assoc % prefix filename))))

(defn get "Returns the value part of an entry."
  [keyw]
  (let [[nspace keyw] (parse-keyword keyw)]
    (-> (get-in @settings [nspace keyw]) first)))

(defn put! "Overwrites a setting with a new value."
  [keyw value]
  (let [[nspace keyw] (parse-keyword keyw)]
    (swap! settings #(update-in % [nspace keyw]
                                (fn [old] `(~value ~(second old) ~(last old)))))))

;; The watcher watches for changes at the settings-atom.  
;; After every change, the respective config file immediately gets stored back to disk.  
(add-watch settings :settings
           (fn [_ _ _ _]
             (for [entry @settings]
               (pspit (str prefix ((key entry) @origin)) (val entry)))))

;; ## Database utilities
;; Currently only one function. This will probably get merged with lopia.database in the future.  
(defn lucene-search "If you want to query the lucene database, this function comes in handy. It takes a normal lucene-conform search string, an offset and a maximum number of elements.  
**Notice that the field names all have to be written uppercase.**  
This function will then return a list of vectors, where the first element is the id and the second the table name."
  [search-str offset max]
  (let [res (k/exec-raw (str "SELECT * FROM FTL_SEARCH_DATA('" search-str "','" offset "','" max "')") :results)]
    (reduce (fn [vec rec]
              (conj vec [(-> rec :keys first) (:table rec)]))
            [] res)))