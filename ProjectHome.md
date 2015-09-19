Fully functional Android RSS reader created as a way to learn the new Android platform SDK.  This project is an ongoing effort to better understand and embrace the Android design philosophy, welcoming comments and additional contributors.

### Project Status ###

This project is just before release 0.1.  It is a functional news reader, but does not contain all the features you might expect from a full-featured distributable application.

The target audience is primarily developers interested in further exploration of the Android SDK.  Source code is available, and can be accessed through the Subversion repository hosted here.

### Screenshots ###

![http://android-rss.googlecode.com/files/rsschannellist2.png](http://android-rss.googlecode.com/files/rsschannellist2.png)
![http://android-rss.googlecode.com/files/rsschanneladd.png](http://android-rss.googlecode.com/files/rsschanneladd.png)
![http://android-rss.googlecode.com/files/rsspostlist.png](http://android-rss.googlecode.com/files/rsspostlist.png)
![http://android-rss.googlecode.com/files/rsspostlist3.png](http://android-rss.googlecode.com/files/rsspostlist3.png)

### To-Do ###

There are a number of issues left unresolved with the project currently:

  * There is a background service in place to handle synchronization of channels on an ideally configurable interval.  Unfortunately, I have not found a graceful way to report status back to the main UI to display the synchronization progress on a per-channel basis so that users may see which channels are currently refreshing.  Not a necessary component for the final product, but it's something I'd like to figure out before I enable the service.

**UPDATE:** See my [Asynchronous Service Example](http://devtcg.blogspot.com/2008/01/asynchronous-service-example.html) for an example of how this could be done.

  * Rewrite RSSChannelRefresh using a more flexible, generalized approach.  This was written quick-and-dirty just to get data for testing, and now desperately needs to be revisited.

  * I am not confident that I have correctly implemented [onFreeze](http://code.google.com/android/reference/android/app/Activity.html#onFreeze(android.os.Bundle)), [onPause](http://code.google.com/android/reference/android/app/Activity.html#onPause()), and [onResume](http://code.google.com/android/reference/android/app/Activity.html#onResume()) in some of my activities.

  * Unit testing.

  * Google needs to fix some of their bugs too :)