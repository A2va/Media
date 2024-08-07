add_rules("mode.debug", "mode.release")

package("oboe")

    set_homepage("https://github.com/google/oboe")
    set_description("Oboe is a C++ library that makes it easy to build high-performance audio apps on Android.")
    set_license("Apache-2.0")

    add_urls("https://github.com/google/oboe/archive/$(version).tar.gz",
                "https://github.com/google/oboe.git")
    add_versions("1.6.1", "855ea841a93bf304065e5152909983b1b85ffabb")

    add_deps("cmake")

    on_install("android", function (package)
        local configs = {}
        table.insert(configs, "-DCMAKE_BUILD_TYPE=" .. (package:debug() and "Debug" or "Release"))
        import("package.tools.cmake").install(package, configs)
    end)
package_end()

add_requires("ffmpeg ~4.4")
add_requires("oboe")

target("media")
    set_kind("shared")
	add_packages("ffmpeg", "oboe")
    add_includedirs("src/main/cpp")
    add_files("src/main/cpp/*.cpp")

    add_packages("ffmpeg", "oboe")