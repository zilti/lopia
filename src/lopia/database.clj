(ns lopia.database
  (:use korma.incubator.core
        korma.db)
  (:require [lopia.util :as u]))

(declare block log tag tag_group block_tag)
(defentity block
  (has-many log))
(defentity log
  (belongs-to block))
(defentity tag
  (belongs-to tag_group))
(defentity tag_group
  (has-many tag))
(defentity attachment)

;;*********************************************
;; Helpers
;;*********************************************
(defn entry-exists?
  [e k v]
  (not (nil? (select e (where {k v})))))

(defn block-type
  [block-id]
  (-> (select block
             (fields :block_type
                     (where {:id block-id})))
     first :block_type))
;;*********************************************
;; Meta workers
;;*********************************************
(defn gen-next-remind
  [last-remind rule])

(defn apply-meta ;; TODO: Change to block-id instead of block-type, give block-id as first arg to function
  [action block-type & args]
  (let [meta (filter #(= (:table %) block-type) (u/get ::block-types))
        fun (read-string (action meta))]
    (apply fun args)))

;;*********************************************
;; Action functions
;;*********************************************

(defn add-log-entry
  [block-id {:keys [log_type title text] :as log-entry}]
  (let [block-type (block-type block-id)]
    (-> (insert log
               (values (merge {:id block-id}
                              (apply-meta :on-log-change block-type log-entry)))) first val)))

(defn create-tag-group
  [{:keys [title description] :as tag-group}]
  (-> (insert tag_group
             (values tag-group)) first val))

(defn update-tag-group
  [id {:keys [title description] :as tag-group}]
  (update tag_group
          (where {:id id})
          (set-fields tag-group)))

(defn delete-tag-group
  [id new-id]
  (transaction
   (update tag
           (where (= :tag.id id))
           (set-fields {:id new-id}))
   (delete tag_group
           (where (= :tag_group.id id)))))

(defn create-tag
  [tag-group {:keys [title description tag_type allowed_values meta] :as v}]
  (when (and (entry-exists? tag_group :id tag-group)
           (not (entry-exists? tag :title title)))
    (-> (insert tag
               (values (merge {:tag_group_id tag-group}
                              v))) first val)))

(defn update-tag
  [id {:keys [tag_group_id title description tag_type allowed_values meta] :as values}]
  (when (entry-exists? tag_group :id tag_group_id)
    (update tag
            (where (= :tag.id id))
            (set-fields values))))

(defn delete-tag
  [id]
  (transaction
   (delete block_tag
           (where (= :block_tag.tag_id id)))
   (delete tag
           (where (= :tag.id id)))))

(defn assoc-tagval
  [tag-id block-id tag-value]
  (when (and (entry-exists? block :id block-id)
           (entry-exists? tag :id tag-id))
    (insert block_tag
            (values (apply-meta :on-tag-add
                                (block-type block-id)
                                {:block_id block-id
                                 :tag_id tag-id
                                 :tag_value tag-value})))))

(defn update-tagval
  [tag-id block-id tag-value]
  (update block_tag
          (where {:block_id block-id
                  :tag_id tag-id})
          (set-fields {:tag_value (apply-meta :on-tag-change
                                              (block-type block-id)
                                              {:block_id block-id
                                               :tag_id tag-id
                                               :tag_value tag-value})})))

(defn dissoc-tagval
  [tag-id block-id]
  (apply-meta :on-tag-remove
              (block-type block-id)
              {:tag_id tag-id
               :block_id block-id})
  (delete block_tag
          (where {:tag_id tag-id
                  :block_id block-id})))

(defn add-attachment
  [block-id {:keys [mime filename data] :as v}]
  (when (entry-exists? block :id block-id)
    (-> (insert attachment
               (values (apply-meta :on-attachment-add
                                   (block-type block-id)
                                   v))) first val)))

(defn drop-attachment
  [attachment-id]
  (delete attachment
          (where (= :id attachment-id))))

(defn boot []
  (u/load-file! "database.clj"))