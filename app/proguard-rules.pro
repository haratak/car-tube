# Release builds do not minify. These rules keep the car host entry points
# if minification is turned on later.
-keep class app.cartube.auto.CarTubeService { *; }
-keep class * extends androidx.car.app.Session { *; }
-keep class * extends androidx.car.app.Screen { *; }
