(ns lopia.mail ""
  (:require [lopia.util :as u]))

(defn send-mail
  [{:keys [to cc bcc subject text]}]
  (let [props (java.util.Properties.)]

    (doto props
      (.put "mail.smtp.host" (u/get ::send.host))
      (.put "mail.smtp.port" (u/get ::send.port))
      (.put "mail.smtp.user" (u/get ::send.user))
      (.put "mail.smtp.socketFactory.port"  (u/get ::send.port))
      (.put "mail.smtp.auth" "true"))
    
    (if (= (u/get ::send.ssl) true)
      (doto props
        (.put "mail.smtp.starttls.enable" "true")
        (.put "mail.smtp.socketFactory.class" 
              "javax.net.ssl.SSLSocketFactory")
        (.put "mail.smtp.socketFactory.fallback" "false")))
    
    (let [authenticator (proxy [javax.mail.Authenticator] [] 
                          (getPasswordAuthentication 
                            []
                            (javax.mail.PasswordAuthentication. 
                             (u/get ::send.user) (u/get ::send.password))))
          to-recipients (reduce #(str % "," %2) to)
          cc-recipients (reduce #(str % "," %2) cc)
          bcc-recipients (reduce #(str % "," %2) bcc)
          session (javax.mail.Session/getInstance props authenticator)
          msg     (javax.mail.internet.MimeMessage. session)]
      
      (.setFrom msg (javax.mail.internet.InternetAddress. (u/get ::send.from)))
      
      (.setRecipients msg 
                      (javax.mail.Message$RecipientType/TO)
                      (javax.mail.internet.InternetAddress/parse to-recipients))
      
      (.setRecipients msg 
                      (javax.mail.Message$RecipientType/CC)
                      (javax.mail.internet.InternetAddress/parse cc-recipients))
      
      (.setRecipients msg 
                      (javax.mail.Message$RecipientType/BCC)
                      (javax.mail.internet.InternetAddress/parse bcc-recipients))
      
      (.setSubject msg subject)
      (.setText msg text)
      (javax.mail.Transport/send msg))))

(defn getFrom [message](javax.mail.internet.InternetAddress/toString (.getFrom message)))
(defn getReplyTo [message] (javax.mail.internet.InternetAddress/toString (.getReplyTo message)) )
(defn getSubject [message] (.getSubject message))

(defn read-multipart-content
  [multipart]
  (if (string? (.getContent multipart))
    (.getContent multipart)
    {:file-name (.getFileName multipart)
     :content (let [in (.getInputStream multipart)
                    b (.read in (byte-array (.available in)))]
                b)}))

(defn get-mail []
  (let [props (java.util.Properties.)
        session (javax.mail.Session/getDefaultInstance props)
        store (.getStore session (u/get ::receive.protocol))
        _ (.connect store (u/get ::receive.host) (u/get ::receive.user) (u/get ::receive.password))
        folder (. store getFolder (u/get ::receive.folder))
        _ (.open folder (javax.mail.Folder/READ_WRITE))
        messages (.getMessages folder)]
    (for [message messges]
      {:from (getFrom message)
       :reply-to (getReplyTo message)
       :subject (getSubject message)
       :contents (reduce
                  (fn[coll part]
                    (conj coll (read-multipart-content part)))
                  [] ((.getContent message) parts))})))

(defn delete-mail [msg]
  (.setFlag msg javax.mail.Flags.Flag/DELETED, true))

(defn boot []
  (u/load-file! "mail.clj"))
