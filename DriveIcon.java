/**
 * The official Google Drive triangle logo (provided by the person building
 * this app), used in place of a generic emoji wherever Drive needs its own
 * distinct, recognizable icon - the Sessions page's pinned Drive row, the
 * sidebar's Drive-mode Home shortcut, and the Settings "Connect Google
 * Drive" section (see SessionsHandler.java, SidebarRenderer.java,
 * SettingsHandler.java).
 *
 * Embedded as a base64 data URI directly in a Java string, rather than as a
 * separate static file the server would need a new route to serve: this
 * project has no build step that copies non-.java resource files anywhere
 * (see TODO.md/README.md - it's compiled straight from source, e.g. via
 * BlueJ or a plain javac/build-jar.bat), so a real static file would
 * silently go missing on anyone else's checkout. A data URI needs nothing
 * extra to ship correctly. Resized down to 64x64 first (source image was
 * much larger) since that's larger than this ever needs to render and kept
 * the embedded string small.
 */
public class DriveIcon {

    public static final String DATA_URI =
        "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAEAAAABACAYAAACqaXHeAAAMiUlEQVR42u2be4zc1XXHP+fe38zszOzOrr34gYO3uITG2CKEQpNgmxhRiIlFBDSdVdJWSWlpUesmUYxNgJTubkTBGDAqbZMQtVHbVH3s1EQOedSE1CaRHQyYEFwbRIBUBNiArNjex+zu/H73nv7xm1l7va95GYzaK412tJq5c8853/v9nnPu/cH/8SGnbGZF6MU2Za5eHIK+c9zan7fNjpL2Y98ZCOjB0IfnliWdzAuuIuQilHZQfxJCFERPWoGfFGcrwxgOMqQ7ufOVAVVEmoyEoOmR7y44tq/ewCtv/jlhaTEt0lh0vLB4funI098xW0X8lp4eTG8fKjTHEdJ04/tXb+WMls28ehR+9kaENXUv1YriSpZ73v3LYNP5g4Rv8rXkOv5Q+7HSjTt9HHA88tfRlniIoSjEiOV/3jQcG4GgdicYAXXCe7Ilnjn/F5oQDc08kvySDbKOL6liRRp3QuMOUAR6hIe/1UKYPkjSdDHuwBrDaAleGqgz+uAiw7dXvMH6zlGiCB8kUSIG8SxnL4cBpA/fyPJNww4o5A3S5ymlNtEWnM2484gYnEImBfPbwPmaXF0xfn3nCOs7R3ERBIKhBLQyD6VP+vCsbDyAjU3Qg6EXZfslXQT2IEoahyDleUUgcvDi6/FfkaoWJGV2/sl7B3hPNkRdvCXKeFMSeCIukit4tlE+aAwBK/OCoKjZQkuQxalOGA/gFZIBLGiP30t10feR4dNnDrK8LcQfNz72j0cJCHDc9/aSYIX4vrHqUhLBDyg5h8rMycpLAzBammTNtNFQYWEy4rkLBsgFHpnOb4qjFUuRa+VKdjSCgsYQoD0GZ+5HZlFlBayBRfPmdLkIeCf85dJjdKQ8fmbqEEIUzz26ixbyqGp9wazPAT1rg1j2Hr2etuAiRiMHM0RfgMhDLgPtmfi9zER8wsUdY/z+wuEK8c1kvmEcTzvnUuLTInh215cq1+4AReAxzzfWdmDkjjLry9wbTWMUzJYYCdzfdQRjqCZvMIzgCfiCfpczuQynPbXbU7sDCvk414+i22kNFlPyvqp5nEI6CZ25KbJoBVxo+MSCYdbMG8dF8f/mcKoQ4cnQDtwhgtYji1Lznpc+5aEPLcfqMzgNYkRUOY8QG//iAIQRiEx8OWOUgxcMsLQlQn0NkREcAYLyfrmc/bUSYm0IKBySmIPdvSRtEq9akxM9kJgsixXZ+/ySQboyUSx7tazJAwkMJbadWhmsyN5/fGg9Gfn2rMQ3568KvDSAGR1DsSxrCTlwwQAtUgucppHFEbrlwxRqQYGp2lH5FUr/iiTi78OpUqfsUMHMoo640eFFt3YdJZNQVOtMTKQsi3C3fpNMLbJYnQN61lqkz2Pm/xltwfKJfL9ezDnF5tK41hyXtw/LxxYUcWEVxDebHeM4ciwjxUYRfLW2zf2TFWn5jbULKbnnsJIjVJmU8tazYiNKMfT7M0/re3MlqxFiG5lRUQIUGCFkJet4lV5krmpxbi+tzAt9eEbDO8gGHYTeN2o8qk5arfh09t735Uq3m1YRS4O1/XFZbAPurFYWpSriK6y6mJTdR6SKNticVJSEqHg9mjvaec7R7I6in8fLJmBJeR+bBh3hCDCErJIP8/hchGiqZO1tBMagzWjDqSdtjXp6h6/fcVS6pWSUW0nFdWUTGjSU84L7qiFCM3eba83HaU1cSrEB2Tu+OkeLNQxG/83h0a847THar5Yr+WcG2UcGCw23uSxFHG2sYiefkG6czoJaM2O+f3CFsvPKLHA3Ja9z5vvV5wCCyk3cuD8sJ1ZI3OTYiKtbXKfywTiK5S7dSRZmlkUzc77f5xks3kxb0EXJ+Yb3pqojm7AUo2+S/+EjFYRJN077sfKb7GWUf6UVizaMAkMJRxtdKJtmqxbNtLKXL3h2XLqMhNnESORBTMM704owHo3RYjeh5cSqMg6WIxRwK0VGsE3hA8sIngSb9Xt0zVQtmhnbXKHeTYvNTGlz1R39wFDSB/joD35KbzmxqiC2L46QXMkrhNxDK4bmyKKSJkvEXTPJokwve6svIx3sYrwZxIcnMELkB0iXzmPfE8P0lpubk3xULgMeIY1wkCRdlJokiwkMY6yRq9h7siyaKc7oz1sM25p2ZqSqtBjB622sf2IQ1prpTnrLZ34i6xhBuYVkE2XRIjC9LB53gOYN3QUHr99Aa+LChqq9E2UvHViGwn389p5/oj9v6XssmqVIjAlxHf/GIHvINlEWc3yQR/hd6cbpruNnomZC9noLSv8H55OQL1bV5qq27PWqqNlY8/l+wEYifFNl0XCn7qKVy/AVNJhJbS5sD9lgISXf+N6Lic8y5v6F/A/3TvDLXGutyOIVPMEYXy/LYtQEWfS0sZRxbj6xWjTlhXkKq1eSMn/CcFR/qTupMjNC0Q1hglvixKpQPQKOy+IXKDJIgGkCHxiG8STZqN/lbGJsGnNC7reNpEnU3Oaaae9nrCFyW/jYY69CBWFVIjb+rJEreI2ILWSbJIsOpYUshi0iKIXKYV1hzdW0Bg9TDJsje0kjlPzLuOh88uvGoU9r5YAJWfwRKYY5QJJfZRxFmiCLKSyjrJF17DE8eFEG0a04r83JxDWGv9eb6X58lMIhqeeC04QsrmIUz+dJxB20pshi7MKt2o81nJH5YzLBeYw5bXzvqyOTsAyH/0V+z0PVEt+csngV2xliN9mm1AlxipxmFTk+HgDXE6HU14+dTHxGhFA9tGyiRw1Hvmro6W8oal89cqMhfwVF86nNqbD0pIgX47Wqo/ZZURBfx7pReOgDYxhJzQ4urY74stYyPHwvv3VgM00clWxIH2UbOT5HkYjGLngpFsFxRNi8002q9mQ6g6dxgOiUrU/SQnrVU2hrEfUCxk+z//20xHkymk74nsELYvyZ8lrrj1+7ZnVn8bBiE2JOnnq6tcssjUAlEv7g1QFMsAgfxVJR7TbQkzI+56BzISxOciqGBZyFDc9u528O3opLzseqqx8BcZZ6WLjxhX8g0fYpSkMOqVMCVSGZgjPPAsWjRpvE2ScEzWPEizGY/Y//DucPHsDZLLYeTlQcgbU412+ArYRjo0hSUFHUUNerY0F5JxmDYMs1WNNeKsaixoQmYNM5n4vPFhXwUutLUREiHxLJPYYHf+0QGv01qZypS2K8h2wrZNI0dmFt7uHEYEPPI4su4VuLPoINj+FqBa2qJxEYPH8nj/34KUOPGsaDuxg/9gY2ZdAazTAGOubzVt3lVkBU2XzuZxkP2hCtqZOqGCNE0RESrlfj1e82/OOyo7jodhJpQ9XNf4mjn+uAZOItc4AXg4k8z7efzZe6fg9TCwpUPYE1OP9F+d6zb7J2rRFQIV8wkIeOl54kmb2QsOjKxDs78QUBLFnahJ5prYQYk3h7OMTze6/ljNJhkARmtigoHmsE5w8xZC7k6qsdfX1qYkHPQ0Ecxm6sDgASO6CjM77z8xYPRRDvOZLO8RfnbMBERXTOIFSau3KT7N8fcuiQSEzfxMbn1fLgst2URraTyll0FpFVB+kMtLaecuKbeStYTOj5+7Ou5SfzLsZGw7iZnKDqSASWyD0su5/eqfm8lUJcoxz/xgoUVEgkb6ZUHI0Pq2eCg8TRfxuHlpPRyFpuOnfjXEmP4Pw41m9SEFYUdGpTtE88a3dbvvwrL+PH76clZ1D108peaw7SKd7up3gqsvj9he9nx+L1M8iiulj2/APy/WdfIJ83J94ZOEk/VOhB+PnhLImhQ5jEu4jGJ5fJYmLiCyynw2NMRj1qhXcP/5wDj19HQj3CROfdY0Tw/hdYex6P7h+KjUSnOxeIQbV7t+FrC4bw7jaC9AnboCx77R2QOD2Mn5BF5/lpexcPdH0SEw4eR4FqrPteb5NH9x+ryN5ctRLk+y2FvOeGF39Equ0DhMMOxZJIwpKzOJVP2zUii7lomOf2XseisTfwJumsNRYX7WPXM5eQz5sK8U1/MDJdvWvNZ1AHYgX1jnmds972fvsIUTDec7SljdvP2YBEI85L+b6t6Gdme8BqZmvy/ZZCt+OPXvhTErm/JZ2C+R2uKcdVp2iUK0N5av8N9n3HniT0mc8mdz/5wImyV70DAPJqKYjjhuevYem5W8ia5Qin7TAK3sJHXn/qZ9/Zc82tsvf1f5/NeKrazBUk/JWmGIouxwS/jtIxqQJRzKSZlKnX6OLPmym9+qmVTO2fqfzPchiJnuBdwX/ySRnpz+dtd6HQhMfr8v2Wd9AwgOaru81WA6BV6Mdw8HTeBMBKlG781Kbl/49px/8ClL2zg6tgMPQAAAAASUVORK5CYII=";

    // Ready-to-use <img> tag at a given pixel size (square). Callers that
    // need extra attributes (class, title, etc.) can still build their own
    // <img src="' + DATA_URI + '"...> directly instead.
    public static String img(int sizePx) {
        return "<img src='" + DATA_URI + "' width='" + sizePx + "' height='" + sizePx +
               "' alt='Google Drive' style='vertical-align:middle;object-fit:contain;'>";
    }
}
