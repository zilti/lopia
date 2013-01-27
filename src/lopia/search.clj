(ns lopia.search
  (:use korma.incubator.core)
  (:require [clojure.string :as string]))

(defn transform-lucene-results "Transforms the java arrays in lucene results into clojure vectors."
  [results]
  (map
   (fn[record]
     (merge record
            {:keys (into [] (:keys record))
             :columns (into [] (:columns record))}))
   results))

(defn fulltext-search-str "Takes a map whose keys correspond to the table columns that can be searched and generates a lucene query string."
  [input]
  (reduce (fn[string entry]
            (let [col (string/upper-case (name (key entry)))
                  src (str "\"(" (val entry) ")\"")]
              (str string col ":" src ",")))
          ""
          (remove #{:offset :max} (keys input))))

(defn generate-lucene-entity
  [{:keys [offset max] :as reqmap}]
  (-> (k/create-entity "lucene")
      (table (raw (str "ftl_search_data('" (fulltext-search-str reqmap) "'," (if (nil? offset) 0 offset) "," (if (nil? max) 0 max) ")")) :lucene)))

(defn lucene-search
  [{:keys [offset max] :as input}]
  (let [res (-> (select* (generate-lucene-entity input))
               (select))]
    ticket-ids))

(defmacro col-search
  [where-clause]
  `(->> (select tickets
              (fields :ticket_id)
              (where ~where-clause))
        (map :ticket_id)))

(defn search "Returns a list of ticket ids for which the search returned a result, sorted by relevance descending."
  [{:keys [fulltext colsearch tables] :as input}]
  (let [fulltext-res (when-not (nil? fulltext) (lucene-search fulltext))
        colsearch-res (when-not (nil? colsearch) (col-search colsearch))
        combined-res (cond
                      (and fulltext-res colsearch-res)
                      (filter (set fulltext-res) colsearch-res)
                      
                      (not (nil? fulltext-res))
                      fulltext-res
                      
                      (not (nil? colsearch-res))
                      colsearch-res
                      
                      :else
                      nil)]
    (if-not (nil? tables)
      (filter (set tables) combined-res)
      combined-res)))
