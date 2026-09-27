package data;

/**
 * One row of testData.json ACCOUNTS: the admin a suite logs in with on an account / tenant code.
 * ENV empty = the same admin on every env; otherwise the env it belongs to (Test / Stage ...).
 */
public class Account {

    public String CODE = "";
    public String ENV = "";
    public String ADMIN = "";
    public String PASSWORD = "";
}
