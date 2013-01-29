(ns lopia.database
  "Lopia uses the [h2 Database](http://www.h2database.org) to manage all its data.
The database is organized in a modular, easy-to-expand way. The central building block for all entries is the table named... block.  
With this architecture, it is dead-simple to add new types of blocks."
  (:use korma.incubator.core
        korma.db)
  (:require [lopia.util :as u]))

(declare block log tag tag_group block_tag)

(defentity ^{:doc "The block is the central element of the system.
It contains the elements used in all subtypes and keeps the block together with the logs, tags and attachments.  
Fields:  
- **id** int auto_increment primary key,  
- **title** varchar,  
- **opener** varchar  ldap-sAMAccountName,  
- **opener&#95;next&#95;remind** timestamp,  
- **opener&#95;gen&#95;remind** varchar serialized-clojure,  
- **supporter** varchar ldap-sAMAccountName,  
- **supporter&#95;next&#95;remind** timestamp,  
- **supporter&#95;gen&#95;remind** varchar serialized-clojure,  
- **opened** timestamp,  
- **closed** timestamp,  
- **due** timestamp,  
- **block_type** varchar,  
- **target** int,  
- **followup** int  
*target* specifies the id of the entry of the table specified at *block_type*, while with *followup*, a followup block can be specified."} block
  (has-many log)
  (has-many attachment)
  (many-to-many tag :block_tag
                {:lfk :block_id
                 :rfk :tag_id}))

(defentity ^{:doc "The **log** table contains all log entries added to a block and ensures that they are in a consistent state and full-text-indexed.  
Fields:  
- **id** int auto_increment primary key,  
- **block_id** int references-block,  
- **log_type** varchar,  
- **log_title** varchar,  
- **log_text** clob"} log
(belongs-to block))

(defentity ^{:doc "The **tag_group** table allows us to create groups of tags. This is interesting for different purposes.  
While it's still not possible to have multiple tags with the same name, you can fetch tags based on a group and use that
in your programming logic.  
Fields:  
- **id** int auto_increment primary key,  
- **title** varchar,  
- **description** varchar"} tag_group
  (has-many tag))

(defentity ^{:doc "Usually, **tag**s only allow you to, well, *tag* something. But the tags in the tag table can do more!  
Tags can be of different types and, in some circumstances, hold values.  
Fields:  
- **id** int auto_increment primary key,  
- **tag&#95;group&#95;id** int references-tag_group,
- **tag_title** varchar,  
- **tag_description** varchar,  
- **tag_type** varchar,  
- **allowed_values** varchar,  
- **meta** clob serialized-clojure"} tag
  (belongs-to tag_group)
  (many-to-many block :block_tag
                {:lfk :tag_id
                 :rfk :block_id}))

(defentity ^{:doc "The **block_tag** table connects tags to blocks as the name says.  
If it's a tag with a value, the value also is stored inside this table.  
Fields:  
- **block_id** int references-block,  
- **tag_id** int references-tag,  
- **tag_value** varchar"} block_tag)

(defentity ^{:doc "Finally, in the table **attachment** you can store attachments assigned to a block.  
Fields:  
- **id** int auto_increment primary key,  
- **block_id** int references-block,  
- **mime** varchar,  
- **filename** varchar,  
- **content** blob"} attachment
  (belongs-to block))


;; ## Helper functions
(defn entry-exists? "Checks if a row in an entity, where key = value, exists."
  [entity key value]
  (not (nil? (select entity (where {key value})))))

(defn block-type "Returns the type of a block.
The type name of a block is always identical of the form table it references."
  [block-id]
  (-> (select block
             (fields :block_type
                     (where {:id block-id})))
     first :block_type))

(defn block-type-exists? "Looks up the configuration to see if the given block type exists.
This does not ensure that the block type has been properly installed!"
  [type-name]
  (= 1 (count (filter #(= % type-name) (->> (u/get ::block-types) (map :table))))))

;; ## Meta workers
(defn gen-next-remind "Using data from a block and the awesome [clj-time](https://github.com/seancorfield/clj-time) wrapper around joda-time,
this generates the next remind time."
  [last-remind rule])

(defn apply-meta "Applies the metadata of a block and of a certain action type, as configured in the config-file, to an arg-list."
  ;; TODO: Change to block-id instead of block-type, give block-id as first arg to function
  [action block-type & args]
  (let [meta (filter #(= (:table %) block-type) (u/get ::block-types))
        fun (read-string (action meta))]
    (apply fun args)))


;; ## Logs
;; Log entries are a way to store additional, incremental data to a block.
;; While the title shouldn't get too long, the text can be as long as you want without influencing the database performance. You can even store clojure data structures in it.  
;; Both the title- and the text-column are full-text indexed.

(defn add-log-entry "This adds a log entry to the block pointed by the block-id."
  [block-id {:keys [log_type log_title log_text] :as log-entry}]
  (let [block-type (block-type block-id)]
    (-> (insert log
               (values (merge {:id block-id}
                              (apply-meta :on-log-change block-type log-entry)))) first val)))

;; ## Attachments
;; If you ever need to upload large, binary data into the database, attachments are the way to go.  
;; They are searchable by their mime type and their fulltext-indexed filename, and the content can get as large as your harddrive.

(defn assoc-attachment "Loads a new attachment into the database and assigns it to the given block-id."
  [block-id {:keys [mime filename data] :as v}]
  (when (entry-exists? block :id block-id)
    (-> (insert attachment
               (values (apply-meta :on-attachment-add
                                    (block-type block-id)
                                    v))) first val)))

(defn dissoc-attachment "Deletes an attachment."
  [attachment-id]
  (delete attachment
          (where (= :id attachment-id))))

;; ## Tags and Tag Groups
;; ### Tags
;; The tags in Lopia are more than what you might first think. They aren't just there to tell you that something is tagged. You have the choice between four types of tags: :mark, :single-val, :single-val-restricted, :multi-val and :multi-val-restricted.  
;; Let's see what the differences are:  
;; - **:mark** marks a block just by its existence, and there's nothing else you can do to it.  
;; - **:single-val** is a tag taking one value, without restriction to what it is, and it's full-text indexed.  
;; - **:single-val-restricted** is, like :single-val, taking one value, but additionally it's restricting the selection of possible values.  
;; - **:multi-val** and **:multi-val-restricted** are the multi-value siblings of the two one-value-taking tags.  

(defn create-tag "Creates a new tag and assigns it to a tag group."
  [tag-group {:keys [title description tag_type allowed_values meta] :as v}]
  (when (and (entry-exists? tag_group :id tag-group)
           (not (entry-exists? tag :title title)))
    (-> (insert tag
               (values (merge {:tag_group_id tag-group}
                              v))) first val)))

(defn update-tag "Changes the values of the tag with the given id."
  [id {:keys [tag_group_id title description tag_type allowed_values meta] :as values}]
  (when (entry-exists? tag_group :id tag_group_id)
    (update tag
            (where (= :tag.id id))
            (set-fields values))))

(defn delete-tag "Deletes a tag and all values stored which are related to the tag."
  [id]
  (transaction
   (delete block_tag
           (where (= :block_tag.tag_id id)))
   (delete tag
           (where (= :tag.id id)))))

;; ### Tag Groups
;; Tag Groups help you sort your tags into probably meaningful categories. Actually they force you to do so - every tag needs to be assigned to a tag group.  
;; Currently, the benefit you get out of it is that you can select tags by group, i.e. to select all tags of a certain group. This can be useful to give the user a selection, if you don't want to use tag values or need them for different things.  

(defn create-tag-group "Creates a new tag group."
  [{:keys [title description] :as tag-group}]
  (-> (insert tag_group
              (values tag-group)) first val))

(defn update-tag-group "Changes the values of the tag group denoted by the id."
  [id {:keys [title description] :as tag-group}]
  (update tag_group
          (where {:id id})
          (set-fields tag-group)))

(defn delete-tag-group "Deletes a tag group.  
You also need to provide the id of an existing tag group to move the tags to."
  [id new-id]
  (transaction
   (update tag
           (where (= :tag.id id))
           (set-fields {:id new-id}))
   (delete tag_group
           (where (= :tag_group.id id)))))

;; ### Tag Values  

(defn assoc-tagval "Associates a tag to a block. If the tag takes no value, put in nil, or the addition will fail.  
***This triggers the :on-tag-add meta-function of the block.***"
  [tag-id block-id tag-value]
  (when (and (entry-exists? block :id block-id)
           (entry-exists? tag :id tag-id))
    (insert block_tag
            (values (apply-meta :on-tag-add
                                (block-type block-id)
                                {:block_id block-id
                                 :tag_id tag-id
                                 :tag_value tag-value})))))

(defn update-tagval "Changes the value of the given connection.  
***This triggers the :on-tag-change meta-function of the block.***"
  [tag-id block-id tag-value]
  (update block_tag
          (where {:block_id block-id
                  :tag_id tag-id})
          (set-fields {:tag_value (apply-meta :on-tag-change
                                              (block-type block-id)
                                              {:block_id block-id
                                               :tag_id tag-id
                                               :tag_value tag-value})})))

(defn dissoc-tagval "Dissociates the tag from a block.  
***This triggers the :on-tag-remove meta-function of the block.***"
  [tag-id block-id]
  (apply-meta :on-tag-remove
              (block-type block-id)
              {:tag_id tag-id
               :block_id block-id})
  (delete block_tag
          (where {:tag_id tag-id
                  :block_id block-id})))

;; ## Blocks
;; Blocks are the central building block of Lopia.  
;; They store the information used by all forms, no matter what forms they are. I'd like to show you the block's features:
;; ### Associating users
;; The simplest yet probably most important thing is to associate users to a block. Lopia uses the sAMAccountName attribute of the LDAP database to store a username. You have three types of associations you can make:  
;; - **opener** The opener is your client, the one who issued a ticket or sent you an order. An opener can have and set reminders.  
;; - **supporter** The supporter is the one managing the block and solving the problem associated to it. The supporter, too, can have and set reminders.  
;; - **observers** can be set - This is a Clojure vector or list taking multiple user names. Those can subscribe and unsubscribe themselves, and you can use the list in your scripts.  
;; ### Defining Block Types
;; You'll probably want to define your own block types. Fortunately that's quite simple and can be done both "by hand" in the database config file or programmatically using the settings tool.  
;; You need to add the namespace of your form to the :database/namespaces setting list, and the block definition to the :database/block-types list.  
;; The block-types entry needs the two entries: :table with the table name (which is, at the dame time, the block-type name) and :trigger with the trigger functions.  
;; See below at the trigger-manipulating functions for more info to the triggers.  
;; ### Timestamps
;; You get three timestamp fields for free: opened, closed and due, which are self-describing. opened and closed are set automatically when the block is created or closed respectively.  
;;

(defn create-block "### Creating and manipulating blocks  
Creates a new block with a type associated. This takes three arguments: The block type (= the table name of the type), the block-data as a map of key-value pairs as described in the entity and the type-data, which has to be a key-value map, too."
  [block-type block-data type-data]
  )

(defn update-block
  [id block-data])

(defn close-block
  [id])

(defn delete-block
  [id])

(defn set-reminder "### Reminders
You can set reminders to your block. To be exact, you can set two of them, one for the supporter and one for the opener of the block.  
Because of that, *target* needs to be either :opener or :supporter.  
first-remind needs to be a [timestamp](http://seancorfield.github.com/clj-time/doc/clj-time.coerce.html#var-to-timestamp),
and interval an [interval](http://seancorfield.github.com/clj-time/doc/clj-time.core.html). For the time-related stuff, the awesome [clj-time](https://github.com/seancorfield/clj-time) wrapper for joda-time is used, so you'll want to use it to construct intervals and timestamps."
  [target first-remind interval])

(defn set-block-followup "### Followups  
Sometimes, especially in a ticket system, you need to close a ticket because it's a duplicate, or you want to declare a block as a successor of another one. That's what the followup is for. You can set an another block as a followup for the current, and can easier find related blocks."
  [id followup-id])

;; ### Trigger
;; You can define special trigger for your blocks. As a rule, the trigger functions shall take the same arguments as the functions in which they are executed.  
;; Available are following trigger hooks:  
;; - **:on-create** is executed at block creation time. (create-block)  
;; - **:on-update** is called whenever a block gets changed. (update-block)  
;; - **:on-log-change** gets applied at a log addition. (add-log-entry)  
;; - **:on-supporter-change** is applied when the supporter changes. (update-block)  
;; - **:on-tag-add** is called whenever a tag is added. (assoc-tagval)  
;; - **:on-tag-change** whenever a tag is changed. (update-tagval)  
;; - **:on-tag-remove** is called upon removal of a tag from a block. (dissoc-tagval)  
;; - **:on-attachment-add** is executed when an attachment gets attached. (assoc-attachment)  
;; - **:on-close** finally gets executed when the block is closed. (close-block)  

(defn define-block-type
  [namespaces {:keys [table trigger]}])
;; ## Startup

(defn boot []
  (u/load-file! "database.clj"))

(defn parse-period
  [x]
  (let [sub? (= \T (second x))
        ptype (last x)
        str-num (->> x
                   (drop (if sub? 2 1))
                   butlast
                   (reduce str))
        num (if (.contains str-num ".")
              (Double/parseDouble str-num)
              (Integer/parseInt str-num))]
    (case [sub? ptype]
      [false \Y] (joda/years num)
      [false \M] (joda/months num)
      [false \W] (joda/weeks num)
      [false \D] (joda/days num)
      [true \H] (joda/hours num)
      [true \M] (joda/minutes num)
      [true \S] (if (= (class num) java.lang.Double)
                  (joda/millis num)
                  (joda/secs num)))))
