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
```
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

```
(def cfg {:server-type :peer-server
                 :access-key "myaccesskey"
                 :secret "mysecret"
                 :endpoint "localhost:8998"})

(def client (d/client cfg))

(def conn (d/connect client {:db-name "monumental"}))
```


With a connection in hand we will start by declaring a schema for our monuments

```
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

```
(d/transact conn {:tx-data monument-schema})
```

Then we put some data in the database

```
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
```
(d/transact conn {:tx-data first-monuments})
```

With data in place we can run some queries. Datomic uses its' own query language **Datalog** which is heavily inspired by Prolog.

Get all entities from db
```
(d/q '[:find ?e :where [?e :monument/ref]] (d/db conn))
```
Here we have to bind `?e` to at least one field in order to avoid a table scan

The last argument is the latest database **value** at the very instant we run the query.

The result is a vector of entity ids

We can get any individual entity using the `pull` function.

```
(d/pull (d/db conn) '[*] 17592186045418)
```
`'[*]` indicates that we want to return all attributes for this entity


If we wanted to find all names of monuments in "Pays de la Loire" we could write the following query:

```
(def monuments-reg-q '[:find ?monument-name
                            :where [?e v:monument/tico ?monument-name]
                                   [?e :monument/reg "Pays de la Loire"]])
(d/q monuments-req-q (d/db conn))
```

But bearing in mind the usage of our application we will have to find regions that contains a certain search term:

```
(def monuments-reg-contains-q '[:find ?monument-name
                                :where [?e :monument/tico ?monument-name]
                                       [?e :monument/reg ?reg]
                                       [(.contains ^String ?reg "Ile")]])
(d/q monuments-reg-contains-q (d/db conn))

```

At this point we are pretty happy with Datomic and that it will be suitable for our application.

We step forward and run a Datomic instance in `dev` mode and loading all the monuments.



### Run Datomic in "dev" mode


Start the transactor with the development transactor template - the dev template comes with a H2 database:

```
bin/transactor config/samples/dev-transactor-template.properties
```

Start a Peer Server pointing to your database:

```
bin/run -m datomic.peer-server -h localhost -p 8998 -a myaccesskey,mysecret -d monumental,datomic:dev://localhost:4334/monumental
```

Start the console:

```
bin/console -p 8080 dev datomic:dev://localhost:4334/
```

The console can be useful for running and visualising queries.


### Load the monument data

Head over to the **load-monuments** project to load the complete set of monuments data:

```
git clone https://github.com/poenneby/load-monuments
```

### Integrating Datomic into Monumental

Given you have successfully loaded the monument data we should now have around 45000 monuments
in our database.

Some ideas and application requirements comes to mind:
 - Seamlessly change our datasource from a file to a datomic database
 - We will have to limit search results
 - What if people wanted to search for monuments by their name?

Open up core.clj of the monumental-api.

We "require" the datomic client library

```
(ns monumental.core
  (:require [clojure.string :as str]
            [datomic.client.api :as d]))

```

And then we can create a database connection just like before:

```
(def cfg {:server-type :peer-server
                 :access-key "myaccesskey"
                 :secret "mysecret"
                 :endpoint "localhost:8998"})
(def client (d/client cfg))
(def conn (d/connect client {:db-name "monumental"}))
```

We want to fetch monuments which have regions matching our search term:

```
(def entities-by-reg '[:find ?e
                :in $ ?search
                :where
                [?e :monument/reg ?reg]
                [(.contains ^String ?reg ?search)]])

(d/q entities-by-reg (d/db conn) "Pays")
```

The `in` clause allow us to do parameterized queries. $ is the db value passed to the query and ?search the search term passed.

But we also need to limit the results.

```
(def entities-by-reg '[:find (sample 10 ?e)
                :in $ ?search
                :where
                [?e :monument/reg ?reg]
                [(.contains ^String ?reg ?search)]])

(d/q entities-by-reg (d/db conn) "Pays")
```
The `sample` return a sample of max 10 distinct entities in the search results.

But again with this query we only get the entity ids in our query result.

We further need to `pull` each entity to get the fields we want.

```
(d/pull (d/db conn) '[*] eid)
```

And once we have all the attributes of monuments we must transform to the format our front end is expecting

We write a little function for that:

```
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
of the collection because it is "lazy". For each monument we reconstruct a new map of key/values as required for the front end.

Putting it all together we get the following function definition:

```
(defn monuments-by-region [search]
    (let [monuments (for [eid (flatten (d/q entities-by-reg (d/db conn) search))]
      (pull-entity eid))]
      (transform monuments)))
```
We query for entities by region, and the resulting vector of vectors are flattened before being iterated over to pull each individual
entity followed by their transformation.

Here is the complete code to put in `src/monumental/core.clj`

```
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

(defn pull-entity [eid]
  (d/pull (d/db conn) '[*] eid))

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
      (pull-entity eid))]
      (transform monuments)))
```

Try running the api and you should be getting results by region as before only now they come from our Datomic database!


```
lein ring server-headless
```

Open `http://localhost:3449`


### More requirements

So we've switched our storage to Datomic but some users are still complaining.

- "How am I supposed to find the Eiffel Tower already???"

We need enable search by monument name!

Users should be able to switch between region and monument name searching.

In the front-end we can do this with a radio button to switch between the fields used in search

And we can parameterize the search field in the back end too!


To be continued...


