# Hoogle database for Frege
Generate [hoogle](https://hoogle.haskell.org:8444/) database for Frege from Frege documentation website.

### Generate and deploy hoogle database for Frege
1. Get frege-hoogle-database jar from [here](https://github.com/Frege/frege-hoogle-database/releases)
1. Get frege jar from [here](https://github.com/Frege/frege/releases)
1. Run
    ```
    java -cp path/to/frege-hoogle-database-<version>.jar:/path/to/frege<version>.jar frege.hoogledatabase.Main
    ```
    This would produce a hoogle text database file named `frege-hoogle-database.txt` in the current directory.
    Run with `--help` to see available options.
1. The text database file can be uploaded to [Frege online REPL](http://try.frege-lang.org/hoogle-frege.txt)
by sending a pull request with that file [here](https://github.com/Frege/try-frege/tree/master/try-frege-web/src/main/webapp).
1. Hoogle instance for Frege running [here](https://hoogle.haskell.org:8444/) will be refreshed with the new database within a day.

### To run Hoogle for Frege locally
1. Install [hoogle](https://github.com/ndmitchell/hoogle)
1. Run the following command to convert text database file generated with steps above to binary database:
    ```
    hoogle generate --local=/path/to/directory_containing_frege_hoogle_database_txt --database=frege.hoo
    ```
    This would produce a binary database file called `frege.hoo` in the current directory
1. Then start hoogle server
    ```
    hoogle server -p 9000 --database=frege.hoo
    ```
    Hoogle Server will be running at [http://localhost:9000/](http://localhost:9000/).
