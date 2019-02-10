# Practical Clo*j*ure Persistence


## Prerequisites

You need Java 8 or above installed and JAVA_HOME set.

Install Leiningen:

 - From the website: https://leiningen.org/#install or using brew: `brew install lein`


### Get Datomic

Get *Datomic Starter Edition* which is a fully featured version limited to a year. (requires registration)

https://www.datomic.com/get-datomic.html


## Intro

Our clients are really enjoying our product, they love searching for monuments!
But some complaints are also coming in about the limitations in search results. Like "Where's the Eiffel Tower?!"

We should have seen this one coming. It's time to make the data source a little more sophisticated.

We need to store more monuments and if data changes over time we want to keep a history of changes.

Since we're in Clojure we decide to use Datomic.


## Getting started

In [Practical Clojure](https://github.com/poenneby/practical-clojure) and
[Practical ClojureScript](https://github.com/poenneby/practical-clojurescript) we built the api and the front end of our application **Monumental**

For convenience we have included the 2 projects `monumental-api` and `monumental-front` here.

You can start up the server and figwheel in respective projects to ensure things are working.

In one terminal window:

```
cd monumental-api
lein ring server-headless
```

And in the other:
```
cd monumental-front
lein figwheel
```

You should be able to search for monuments and display details of them at `http://localhost:3449`

## Up and running with Datomic


We got a copy of Datomic starter edition ready to go.

Our api will connect to the peer server using the client library so we add the library dependency in **project.clj**

`monumental-api/project.clj`
```clojure
[com.datomic/client-pro "0.8.28"]
```

In a separate terminal we start up a **peer-server** with an in-memory database

```
$DATOMIC_HOME/bin/run -m datomic.peer-server -h localhost -p 8998 -a myaccesskey,mysecret -d monumental,datomic:mem://monumental
```

With the dependency in place and the peer-server running we can start up a repl and try some database operations
```
lein repl
```

The command starting the peer-server created an in-memory database called `monumental`

We can create a connection to the database using a configuration map:

```clojure
(require '[datomic.client.api :as d])

(def cfg {:server-type :peer-server
                 :access-key "myaccesskey"
                 :secret "mysecret"
                 :endpoint "localhost:8998"})

(def client (d/client cfg))

(def conn (d/connect client {:db-name "monumental"}))
```


With a connection in hand we will start by declaring a schema for our monuments

```clojure
(def monument-schema [{:db/ident :monument/ref
                          :db/valueType :db.type/string
                          :db/cardinality :db.cardinality/one
                          :db/doc "The REF of the monument"}
                         {:db/ident :monument/tico
                          :db/valueType :db.type/string
                          :db/cardinality :db.cardinality/one
                          :db/doc "The TICO of the monument"}
                         {:db/ident :monument/reg
                          :db/valueType :db.type/string
                          :db/cardinality :db.cardinality/one
                          :db/fulltext true
                          :db/doc "The region of the monument"}])
```
For each attribute we declare its' identity, type, cardinality and a helpful doc string. All our attributes are of type `string`.


The schema is then transacted to the database:

```clojure
(d/transact conn {:tx-data monument-schema})
```

In the result returned we can see details about the `:db-before` and `:db-after` the transaction. We also note that the schema is data in the form of `datoms` - facts at a certain point in time.


Then we put some data in the database

```clojure
(def first-monuments [{:monument/ref "PA00109152"
                       :monument/tico "Ch\u00e2teau de la Turmeli\u00e8re (ancien)"
                       :monument/reg "Pays de la Loire"}
                      {:monument/ref "PA00109645"
                       :monument/tico "Prieur\u00e9 g\u00e9nov\u00e9fain (ancien)"
                       :monument/reg "Pays de la Loire"}
                      {:monument/ref "PA00082795"
                       :monument/tico "Ch\u00e2teau du Roc"
                       :monument/reg "Aquitaine"}
                       ])
```



And just like before we transact but with the argument `first-monuments`
```clojure
(d/transact conn {:tx-data first-monuments})
```

With data in place we can run some queries. Datomic uses its' own query language **Datalog** which is heavily inspired by Prolog.

We can get all entities from db
```clojure
(d/q '[:find ?e :where [?e :monument/ref]] (d/db conn))
```
Here we have to bind `?e` to at least one field in order to avoid a table scan

The last expression `(d/db conn)` resolves to the latest database **value** at the very instant we run the query. As you may have guessed
we can also get historic database values. Datomic never forget...

The result is a vector of entity ids:
`[[17592186045418] [17592186045419] [17592186045420]]`

We can get any individual entity using the `pull` function.

```clojure
(d/pull (d/db conn) '[*] 17592186045418)
```
`'[*]` indicates that we want to return all attributes for this entity


If we wanted to find all names of monuments in "Pays de la Loire" we could write the following query:

```clojure
(def monuments-reg-q '[:find ?monument-name
                            :where [?e :monument/tico ?monument-name]
                                   [?e :monument/reg "Pays de la Loire"]])
(d/q monuments-reg-q (d/db conn))
```

But bearing in mind the usage of our application we will have to find regions that *contains* a certain search term:

```clojure
(def monuments-reg-contains-q '[:find ?monument-name
                                :where [?e :monument/tico ?monument-name]
                                       [?e :monument/reg ?reg]
                                       [(.contains ^String ?reg "Pays")]])
(d/q monuments-reg-contains-q (d/db conn))

```

Using Datomic from the REPL has given us a basic idea of how it works and it's time to get more data loaded.


### Run Datomic in "dev" mode


First kill the running peer-server.

Then start the transactor with the development transactor template - the dev template runs the transactor with a H2 database:

```
bin/transactor config/samples/dev-transactor-template.properties
```

### Load the monument data

Clone the **load-monuments** project and follow the instructions to load the complete set of monuments data:

```
git clone https://github.com/poenneby/load-monuments
```


With the data loaded, in another terminal, start a Peer Server pointing to your database:

```
bin/run -m datomic.peer-server -h localhost -p 8998 -a myaccesskey,mysecret -d monumental,datomic:dev://localhost:4334/monumental
```

And in another terminal window, start the Datomic console:

```
bin/console -p 8080 dev datomic:dev://localhost:4334/
```

The console can be accessed at http://localhost:8080/browse.

From here we can for example verify the number of monuments loaded.

Select the monumental database from the dropdown and run the following query.
```clojure
[:find (count ?e)
 :where
 [?e :monument/ref]
]
```



## Using Datomic as a data source for Monumental

Given you have successfully loaded the monument data we should now have around 45000 monuments
in our database.

Some ideas and application requirements comes to mind:
 - Seamlessly change our data source from a file to a Datomic database
 - We will have to limit search results
 - What if people wanted to search for monuments by their name?


### REPL driven development

Back in the REPL of `monumental-api`, we will figure out how to load the data and expose it via the API.

First make sure we have a connection to our database:
```clojure
(require '[datomic.client.api :as d])

(def cfg {:server-type :peer-server
                 :access-key "myaccesskey"
                 :secret "mysecret"
                 :endpoint "localhost:8998"})

(def client (d/client cfg))

(def conn (d/connect client {:db-name "monumental"}))

```

To match the existing functionality we need to fetch monuments which have regions matching our search term:

```clojure
(def entities-by-reg '[:find ?e
                       :in $ ?search
                       :where
                       [?e :monument/reg ?reg]
                       [(.contains ^String ?reg ?search)]])

(d/q entities-by-reg (d/db conn) "Pays")
```

The `in` clause allow us to do parameterized queries. `$` is the db value passed to the query and `?search` the search term passed.

But that brings back too many results so we need to limit them.

```clojure
(def entities-by-reg '[:find (sample 10 ?e)
                       :in $ ?search
                       :where
                       [?e :monument/reg ?reg]
                       [(.contains ^String ?reg ?search)]])

(d/q entities-by-reg (d/db conn) "Pays")
```
The `sample` function return a maximum of 10 distinct entities in the search results.

But again with this query we only get the entity ids in our query result.

We also need to `pull` each entity to get the fields we want.

```clojure
(for [eid (flatten (d/q entities-by-reg (d/db conn) "Pays"))]
      (d/pull (d/db conn) '[*] eid))
```


And once we have all the attributes of the monuments we must transform them to the format our front end is expecting

We write a little function for that:

```clojure
(defn transform [monuments]
  (for [m (take-while #(not (empty? (:monument/ref %))) monuments)
          :let [monument {:REF (:monument/ref m)
                          :TICO (:monument/tico m)
                          :INSEE (:monument/insee m)
                          :PPRO (:monument/ppro m)
                          :DPT (:monument/dpt m)
                          :REG (:monument/reg m)}
                ]] monument))
```
Using list comprehension we iterate over all monuments that have the `:monument/ref` attribute set. We are obliged to `take` out
of the collection because it is "lazy". And we `take-while` the `ref` attribute of the monument is not empty.
For each monument we reconstruct a new map of key/values as required for the front end.

Putting it all together we get the following function definition:

```clojure
(defn monuments-by-region [search]
    (let [monuments (for [eid (flatten (d/q entities-by-reg (d/db conn) search))]
      (d/pull (d/db conn) '[*] eid))]
      (transform monuments)))
```

And here is the complete code to put in `src/monumental/core.clj`

```clojure
(ns monumental.core
  (:require [clojure.string :as str]
            [datomic.client.api :as d]))


(def cfg {:server-type :peer-server
                 :access-key "myaccesskey"
                 :secret "mysecret"
                 :endpoint "localhost:8998"})

(def client (d/client cfg))

(def conn (d/connect client {:db-name "monumental"}))


(def entities-by-reg '[:find (sample 10 ?e)
                          :in $ ?search
                          :where
                          [?e :monument/reg ?reg]
                          [(.contains ^String ?reg ?search)]])


(defn transform [monuments]
  (for [m (take-while #(not (empty? (:monument/ref %))) monuments)
          :let [monument {:REF (:monument/ref m)
                          :TICO (:monument/tico m)
                          :INSEE (:monument/insee m)
                          :PPRO (:monument/ppro m)
                          :DPT (:monument/dpt m)
                          :REG (:monument/reg m)}
                ]] monument))


(defn monuments-by-region [search]
    (let [monuments (for [eid (flatten (d/q entities-by-reg (d/db conn) search))]
      (d/pull (d/db conn) '[*] eid))]
      (transform monuments)))
```

We also remove the the line that loads monuments from disk and change the route in `handler.clj` to match the new signature:

```clojure
  (GET "/api/search" [search] (response (monuments-by-region search)))
```

Try starting the API and you should be getting results by region as before only now they come from our Datomic database!


```
lein ring server-headless
```

Open `http://localhost:3449` and you should be able to search for monuments by region as before!


## Unhappy users

We have switched our monuments data source to Datomic but some users are still complaining.

- "How am I supposed to find the Eiffel Tower already???"

We need to enable search by monument name! (TICO)

Users should be able to switch between region and monument name searching.

In the front-end we can do this with a radio button to switch between the fields used in search

And we can parameterize the search field in the back end too!

### Back

We can generify the `monuments-by-region [search]` to `monuments-by [field search]` giving us the flexibility to pass in any valid field to search for.

`src/monumental/core.clj`
```clojure
(defn monuments-by [field search]
    (let [monuments (for [eid (flatten (d/q entities-by (d/db conn) field search))]
      (d/pull (d/db conn) '[*] eid))]
      (transform monuments)))
```

And we add the field parameter to a new `entities-by` query:

```clojure
(def entities-by '[:find (sample 10 ?e)
                          :in $ ?field ?search
                          :where
                          [?e ?field ?reg]
                          [(.contains ^String ?reg ?search)]])
```

And we decide that the api accepts a field query parameter `field=reg` so we need to translate `reg` into the schema attribute `:monument/reg`

```clojure
(def schema-mappings '{:reg :monument/reg
                       :tico :monument/tico})

(defn monuments-by [field search]
    (let [monuments (for [eid (flatten (d/q entities-by (d/db conn) ((keyword field) schema-mappings) search))]
      (d/pull (d/db conn) '[*] eid))]
      (transform monuments)))
```
The `field` argument gets transformed into a **keyword** in order to lookup the corresponding schema attribute name.

Finally we modify the `handler` to use our new function:

```clojure
  (GET "/api/search" [field search] (response (monuments-by field search)))
```

### Front

We add a `selectedField` to our state and set it to `reg` by default.

`src/cljs/monumental_front/core.cljs`
```clojure
(defonce state (atom {:monuments []
                      :search ""
                      :selectedField "reg"}))
```

We then introduce a new `<search-option>` component which updates the `selectedField` state - This component can be reused for as many fields as we wish:
```clojure
(defn set-search-field [searchField]
  (swap! state assoc :selectedField searchField))

(defn <search-option> [value]
  [:div
   [:input.searchRadio {:type "radio" :name "field" :id value :value value :checked (= value (:selectedField @state)) :on-change #(set-search-field value)} ]
   [:label {:for value } value]])

(defn <search> []
  [:div [:div [:input.searchInput
         {:placeholder (str "Search by " (:selectedField @state))
          :value (:search @state)
          :on-change #(fetch-monuments (-> % .-target .-value))}]
   ]
   [:div.searchType
    (<search-option> "reg")
    (<search-option> "tico")]])

```

The `selectedField` state is included in the query parameters when calling the api.

```clojure
(defn fetch-monuments [search]
  (swap! state assoc :search search)
  (GET "http://localhost:3000/api/search" {:params {:field (:selectedField @state)  :search search}
                                           :response-format :json
                                           :keywords? true
                                           :handler #(swap! state assoc :monuments %)}))
```


We can finally search for monuments by their name!! 🎉



## Conclusion

You have now got a first hand experience building an application with Datomic through a practical example.

We are of course only scratching the surface of what is possible with Datomic but I hope you have
now got an idea of what you can achieve with relatively little code.

