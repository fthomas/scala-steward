<p align="center">
  <img src="https://github.com/fthomas/scala-steward/raw/master/data/images/scala-steward-logo-circle-0.png" height="180px">
</p>

# Scala Steward
[![Build Status](https://travis-ci.com/scala-steward-org/scala-steward.svg?branch=master)](https://travis-ci.com/scala-steward-org/scala-steward)
[![codecov](https://codecov.io/gh/scala-steward-org/scala-steward/branch/master/graph/badge.svg)](https://codecov.io/gh/scala-steward-org/scala-steward)
[![Join the chat at https://gitter.im/scala-steward-org/scala-steward](https://badges.gitter.im/scala-steward-org/scala-steward.svg)](https://gitter.im/scala-steward-org/scala-steward?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Follow @ScalaSteward on Twitter](https://img.shields.io/twitter/follow/ScalaSteward.svg?logo=twitter&style=flat&color=blue)](https://twitter.com/ScalaSteward)
[![Typelevel project](https://img.shields.io/badge/typelevel-project-blue.svg)](https://typelevel.org/projects/#scala-steward)
[![Scala Steward badge](https://img.shields.io/badge/Scala_Steward-helping-blue.svg?style=flat&logo=data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA4AAAAQCAMAAAARSr4IAAAAVFBMVEUAAACHjojlOy5NWlrKzcYRKjGFjIbp293YycuLa3pYY2LSqql4f3pCUFTgSjNodYRmcXUsPD/NTTbjRS+2jomhgnzNc223cGvZS0HaSD0XLjbaSjElhIr+AAAAAXRSTlMAQObYZgAAAHlJREFUCNdNyosOwyAIhWHAQS1Vt7a77/3fcxxdmv0xwmckutAR1nkm4ggbyEcg/wWmlGLDAA3oL50xi6fk5ffZ3E2E3QfZDCcCN2YtbEWZt+Drc6u6rlqv7Uk0LdKqqr5rk2UCRXOk0vmQKGfc94nOJyQjouF9H/wCc9gECEYfONoAAAAASUVORK5CYII=)](https://scala-steward.org)
[![Docker Pulls](https://img.shields.io/docker/pulls/fthomas/scala-steward.svg?style=flat&color=blue)](https://hub.docker.com/r/fthomas/scala-steward/)

Scala Steward is a bot that helps you keeping library dependencies
and sbt plugins up-to-date.

See also the announcement blog post:
[*Keep your projects up-to-date with Scala Steward*](https://www.scala-lang.org/blog/2019/07/10/announcing-scala-steward.html)

## Quick start guide

Open a pull request that adds the GitHub repository of your Scala project
to [repos-github.md](https://github.com/scala-steward-org/repos/blob/master/repos-github.md)
([edit](https://github.com/scala-steward-org/repos/edit/master/repos-github.md)).
Once that PR is merged, [**@scala-steward**][@scala-steward] will check
periodically for updates of libraries and plugins in your project and will
open pull requests for updates it found.

## Show us the pull requests!

If you are curious how [**@scala-steward**'s][@scala-steward] pull requests
look like, here are the ones it has created so far:

* [Created pull requests](https://github.com/search?q=author%3Ascala-steward+is%3Apr)
  ([compact](             https://github.com/pulls?q=author%3Ascala-steward+is%3Apr))
* [Merged pull requests]( https://github.com/search?q=author%3Ascala-steward+is%3Amerged+sort%3Aupdated-desc)
  ([compact](             https://github.com/pulls?q=author%3Ascala-steward+is%3Amerged+sort%3Aupdated-desc))

You can also watch what it is currently doing [here](https://gitstalk.netlify.com/scala-steward).

## Wanna have a badge?

A badge is available to show that Scala Steward is helping your repos.
Let's spread Scala Steward to keep Scala ecosystem updated.

[![Scala Steward badge](https://img.shields.io/badge/Scala_Steward-helping-blue.svg?style=flat&logo=data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA4AAAAQCAMAAAARSr4IAAAAVFBMVEUAAACHjojlOy5NWlrKzcYRKjGFjIbp293YycuLa3pYY2LSqql4f3pCUFTgSjNodYRmcXUsPD/NTTbjRS+2jomhgnzNc223cGvZS0HaSD0XLjbaSjElhIr+AAAAAXRSTlMAQObYZgAAAHlJREFUCNdNyosOwyAIhWHAQS1Vt7a77/3fcxxdmv0xwmckutAR1nkm4ggbyEcg/wWmlGLDAA3oL50xi6fk5ffZ3E2E3QfZDCcCN2YtbEWZt+Drc6u6rlqv7Uk0LdKqqr5rk2UCRXOk0vmQKGfc94nOJyQjouF9H/wCc9gECEYfONoAAAAASUVORK5CYII=)](https://scala-steward.org)

```markdown
[![Scala Steward badge](https://img.shields.io/badge/Scala_Steward-helping-blue.svg?style=flat&logo=data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA4AAAAQCAMAAAARSr4IAAAAVFBMVEUAAACHjojlOy5NWlrKzcYRKjGFjIbp293YycuLa3pYY2LSqql4f3pCUFTgSjNodYRmcXUsPD/NTTbjRS+2jomhgnzNc223cGvZS0HaSD0XLjbaSjElhIr+AAAAAXRSTlMAQObYZgAAAHlJREFUCNdNyosOwyAIhWHAQS1Vt7a77/3fcxxdmv0xwmckutAR1nkm4ggbyEcg/wWmlGLDAA3oL50xi6fk5ffZ3E2E3QfZDCcCN2YtbEWZt+Drc6u6rlqv7Uk0LdKqqr5rk2UCRXOk0vmQKGfc94nOJyQjouF9H/wCc9gECEYfONoAAAAASUVORK5CYII=)](https://scala-steward.org)
```

## Documentation

The [`docs`](docs) directory contains documentation about these topics:

* [Running Scala Steward](docs/running.md)
* [Scalafix Migrations](docs/scalafix-migrations.md)
* [Frequently Asked Questions](docs/faq.md)
* [Repository-specific configuration](docs/repo-specific-configuration.md)

## Contributors

Thanks goes to these wonderful people for contributing to Scala Steward:

* [Adam Fraser](https://github.com/adamgfraser)
* [Alex](https://github.com/jhnsmth)
* [Alex Klibisz](https://github.com/alexklibisz)
* [Andrea](https://github.com/Andrea)
* [Andrea Mistretta](https://github.com/andreami)
* [Anil Kumar Myla](https://github.com/anilkumarmyla)
* [Arjun Dhawan](https://github.com/arjun-1)
* [Arulselvan Madhavan](https://github.com/ArulselvanMadhavan)
* [Barry O'Neill](https://github.com/barryoneill)
* [Bayram Kiran](https://github.com/kiranbayram)
* [Brice Jaglin](https://github.com/bjaglin)
* [Cédric Chantepie](https://github.com/cchantep)
* [Christopher Davenport](https://github.com/ChristopherDavenport)
* [Dale Wijnand](https://github.com/dwijnand)
* [Daniel Pfeiffer](https://github.com/dpfeiffer)
* [David Francoeur](https://github.com/daddykotex)
* [Doug Roper](https://github.com/htmldoug)
* [Eldar Yusupov](https://github.com/eyusupov)
* [Erik Erlandson](https://github.com/erikerlandson)
* [Erlend Hamnaberg](https://github.com/hamnis)
* [eugeniyk](https://github.com/eugeniyk)
* [Fabian](https://github.com/fg-devs)
* [Felix Dietze](https://github.com/fdietze)
* [Filipe Regadas](https://github.com/regadas)
* [Frank S. Thomas](https://github.com/fthomas)
* [Georgy Davityan](https://github.com/implmnt)
* [Grzegorz Kocur](https://github.com/gkocur)
* [Guillaume Martres](https://github.com/smarter)
* [Ingar Abrahamsen](https://github.com/ingarabr)
* [Jakub Kozłowski](https://github.com/kubukoz)
* [JCollier](https://github.com/Slakah)
* [Jeff Martin](https://github.com/custommonkey)
* [Jichao Ouyang](https://github.com/jcouyang)
* [Joan Goyeau](https://github.com/joan38)
* [José Eduardo Montenegro Cavalcanti de Oliveira](https://github.com/edumco)
* [kalejami](https://github.com/kalejami)
* [KAWACHI Takashi](https://github.com/tkawachi)
* [kenji yoshida](https://github.com/xuwei-k)
* [Lars Hupel](https://github.com/larsrh)
* [Manuel Cueto](https://github.com/manuelcueto)
* [Marco Zühlke](https://github.com/mzuehlke) 
* [Mark Canlas](https://github.com/mcanlas)
* [MaT1g3R](https://github.com/MaT1g3R)
* [Michael Wizner](https://github.com/mwz)
* [Michel Daviot](https://github.com/tyrcho)
* [miguelpuyol](https://github.com/miguelpuyol)
* [nafg](https://github.com/nafg)
* [Philippus Baalman](https://github.com/Philippus)
* [Piotr Gabara](https://github.com/pgabara)
* [Renato Cavalcanti](https://github.com/renatocaval)
* [Rikito Taniguchi](https://github.com/tanishiking)
* [Robert Stoll](https://github.com/robstoll)
* [Stanislav Chetvertkov](https://github.com/stanislav-chetvertkov)
* [sullis](https://github.com/sullis)
* [TATSUNO Yasuhiro](https://github.com/exoego)
* [Thomas Heslin](https://github.com/tjheslin1)
* [Thomas Kaliakos](https://github.com/thomaska)
* [Victor Viale](https://github.com/Koroeskohr)
* [Yan](https://github.com/yaroot)
* [Yoan Alvarez-Vanhard](https://github.com/tyoras)
* [Zack Powers](https://github.com/Milyardo)
* [Zelenya](https://github.com/Zelenya)

## Community

The following companies are using Scala Steward to manage their dependencies.
Using Scala Steward in your company and don't see it listed here?
Consider creating PR to add your company to the list and join the community.

* [Adform](https://site.adform.com/)
* [Agoda](https://agoda.com/)
* [AutoScout24](https://www.autoscout24.de/)
* [Avast](https://avast.com)
* [Babylon Health](https://www.babylonhealth.com/)
* [Besedo](https://www.besedo.com/)
* [Bitrock](http://www.bitrock.it/)
* [Chartboost](https://www.chartboost.com/)
* [Colisweb](https://www.colisweb.com/)
* [commercetools](https://docs.commercetools.com/)
* [Enliven Systems](https://enliven.systems)
* [Evolution Gaming](https://www.evolutiongaming.com/)
* [Firstbird](https://firstbird.com)
* [Hellosoda](https://hellosoda.com/)
* [HolidayCheck](https://github.com/holidaycheck)
* [iAdvize](https://www.iadvize.com/en/)
* [Iterators](https://www.iteratorshq.com/)
* [LeadIQ](https://leadiq.com/)
* [Lightbend](https://www.lightbend.com/)
* [Ocado Technology](https://ocadotechnology.com/)
* [PlayQ](https://www.playq.com/)
* [Precog](https://precog.com/)
* [Rewards Network](https://www.rewardsnetwork.com/)
* [Septeni Original](https://www.septeni-original.co.jp)
* [Snowplow Analytics](https://snowplowanalytics.com/)
* [SoftwareMill](https://softwaremill.com)
* [Spotify](https://www.spotify.com)
* [SpringerNature](https://www.springernature.com)
* [Tegonal GmbH](https://tegonal.com)
* [Tupl](https://www.tupl.com)
* [wehkamp](https://www.wehkamp.nl/)
* [Zalando](https://en.zalando.de/)

## Participation

The Scala Steward project supports the [Scala Code of Conduct][CoC]
and wants all of its channels (GitHub, Gitter, etc.) to be welcoming
environments for everyone.

## Credit

Scala Steward wouldn't exist without the great [sbt-updates][sbt-updates]
plugin which was used until version 0.6 to find dependency updates.

Thanks goes also to [**@impurepics**](https://twitter.com/impurepics)
for [**@scala-steward**][@scala-steward]'s cute profile picture and to
the maintainers and contributors of the various
[libraries](https://github.com/fthomas/scala-steward/blob/master/project/Dependencies.scala)
this project depends on.

## License

Scala Steward is licensed under the
[Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0).

[CoC]: https://github.com/fthomas/scala-steward/blob/master/CODE_OF_CONDUCT.md
[@scala-steward]: https://github.com/scala-steward
[sbt-updates]: https://github.com/rtimush/sbt-updates
