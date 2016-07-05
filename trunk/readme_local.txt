一、用studio打开nuwa项目，使用如下命令讲插件上传本地仓库
        gradlew -p buildSrc/ clean build uploadArchives
	或者在其他项目目录下用绝对路径gradlew -p D:/haiziwang/sourcecode/AppDev/Nuwa/trunk/buildSrc/ clean build uploadArchives
二、其他项目中引用该插件
    1、setting.gradle文件中添加
        include ':nuwa'
        project( ':nuwa' ).projectDir = new File( '../../Nuwa/trunk/nuwa' )

    2、跟目录build.gradle:
    buildscript {
          repositories {
              maven {
                  //url uri('file:///D:/haiziwang/sourcecode/AppDev/Nuwa/trunk/repo')//nuwa插件库位置--绝对位置
                  url uri('../../Nuwa/trunk/repo')//nuwa插件库位置--相对位置
              }
              jcenter()
          }
          dependencies {
              classpath 'com.android.tools.build:gradle:2.1.0'
              classpath  'cn.jiajixin.nuwa.kidswant:gradle:1.0.0'//引用nuwa插件

              // NOTE: Do not place your application dependencies here; they belong
              // in the individual module build.gradle files
          }
      }

     3、app中的build.gradle:
      apply plugin: cn.jiajixin.nuwa.NuwaPlugin
      nuwa.includePackage = ['com/kidswant']
      nuwa.excludeClass  = ['com/kidswant/ss/app/AppContext', 'com/kidswant/ss/db/KWContentProvider', 'com/kidswant/ss/ui/exception/ExceptionActivity']
      //nuwa.debugOn = true
     4、Application的子类中oncreate方法中添加如下代码
            try {
                 Nuwa.initial(this);
                 Nuwa.loadPatch(this, Environment.getExternalStorageDirectory().getAbsolutePath().concat("/patch.apk"));
             } catch (Exception e) {
                 e.printStackTrace();
             }


三、补丁包制作步骤
    1、正式打包gradlew clean assembleRelease
       该命令执行完毕后，会在app\build\outputs目录下有个nuwa目录，把nuwa目录copy到电脑上任意目录，比如D:\haiziwang\nuwa，
       一定要把最后发布编译的nuwa保存，直到后面又有新版本发布才需要更新，中间打补丁不需要更新nuwa
    2、修改出现bug的文件（java文件，不支持资源文件）并执行如下命令
       gradlew clean assemblePcRelease -P NuwaDir=D:\haiziwang\nuwa
       NuwaDir的值就是上一步中的nuwa
       这里只编译pc渠道，因为每个渠道的java部分的代码是一样的，没必要给所有的渠道都制作补丁包，只要一个渠道，所有渠道公用就行

       另外附上制作所有渠道补丁包的命令
       gradlew clean assembleDebug -P NuwaDir=D:\haiziwang\nuwa(所有渠道,只包括debug版本)
       gradlew clean assembleRelease -P NuwaDir=D:\haiziwang\nuwa(所有渠道,只包括release版本)
       gradlew clean build -P NuwaDir=D:\haiziwang\nuwa (所有渠道,包括debug和release版本)
    3、命令执行完毕后会生成补丁包
       app\build\outputs\nuwa\pc\release\patch\patch-pcRelease.apk
       把patch-pcRelease.apk重新命名成patch.apk作为最终的补丁包发布
       测试的话先把补丁包放到sdcard上，然后重启app即可看到效果
       adb push app\build\outputs\nuwa\pc\release\patch\patch-pcRelease.apk /sdcard/patch.apk
