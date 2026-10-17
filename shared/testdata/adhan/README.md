# Adhan golden test data

Copied unchanged from [batoulapps/adhan-testdata](https://github.com/batoulapps/adhan-testdata)
at commit `d418fb37b3d011af5594e344c06c0e5616db2a5c`. MIT licensed (see `LICENSE`).
Each file lists published times from an official source for one method and city.

Both platform test suites run these through our own wrapper (not Adhan directly),
so they also catch mistakes in how we map settings onto Adhan. `Tehran-Tehran.json`
is skipped because Tehran isn't one of our methods: it needs a Maghrib angle, which
the Kotlin port lacks.
