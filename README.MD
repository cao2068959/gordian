# 1. 简介

## 1.1为什么造这个轮子
~~太闲了~~ 

现在的`spring aop` 已经非常的完善了，但是却有以下几个小缺点

- 当引入了太多第三方`jar` 后，`Pointcut` 的定义被分散到了不同的 `jar`, 你可能不知道在你的业务方法在执行之前会去执行多少方法，这些方法都会干什么。比如`@Transactional` 注解，大家都知道他的原理就是 `aop`，那么我想看看里面他会在我方法执行前，执行后做了什么，就需要去翻文档才能知道他执行`aop`的类在哪里，这算不错的体验了，那如果是你接手了一个古老且没有文档的业务代码，各种`aop`的定义分散在各处，那么确实是一场噩梦，会有很多`隐藏`的代码在你不知道的地方默默工作着。
- 如果我想在非`spring` 项目里去使用`aop`，确实有很多第三方优秀的`aop`框架，但是很多都是基于`动态代理` 实现的，那么使用起来将会对你的业务代码带来一部分的入侵 , 如下所示。
```java
A a = new A();
a.exec();

//动态代理
A a = proxy();
a.exec();
```

而 `gordian`  就是为了解决以上两个问题而存在的， 同时因为是在`编译期`去生成代理代码，性能将会比`动态代理` __更高__ 



## 1.3 为什么叫 gordian

其实这个名字来源于 `gordian worm` , 也是我们常说的 `铁线虫` 了，他会入侵`螳螂` 体内，然后完全控制 `螳螂` 的行为，其实这和我这个框架类似，他将在编译期的时候入侵业务代码，以至于改变这个方法行为


# 2. 使用

下载源码后执行
```shell
mvn clean install
```
然后在项目里引入
```xml
<dependency>
    <groupId>com.chy</groupId>
    <artifactId>gordian</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

包中提供了一个接口 `Gordian`, 新建一个类去实现这个接口, 在实现方法中，可以尽情的写方法执行前，以及方法执行后的逻辑，`parasitifer.exec()` 代表你业务逻辑要执行的方法
```java
public class GordianImp implements Gordian {
   
    @Override
    public Object control(Parasitifer parasitifer)  throws Throwable {
        System.out.println("GordianImp 执行开始 @@@@@@@@@@@@@@ --->");
        Object result = parasitifer.exec();
        System.out.println("GordianImp 执行结束 ################");
        return result;
    }
}
```

然后在你你要切入的方法的地方打上 `@Parasitic`注解，并且指定你刚刚写的 `Gordian` 的实现类
```java
public class Chy {
    
    @Parasitic(gordians = {GordianImp.class})
    public void test() {
        System.out.println("执行了 test 方法");
    }
    
}
```
最后直接去 `new` 出这个类，调用即可
```java
 public static void main(String[] args) throws InterruptedException {
        Chy chy = new Chy();
        chy.test();
    }
```

执行后可以看到 

![在这里插入图片描述](https://img-blog.csdnimg.cn/20200829174519662.png#pic_center)

# 2.1 . 多个 Gordian
仔细的读者可以看出来，` @Parasitic(gordians = {GordianImp.class})` 注解中传入的 `gordians` 是一个数组，那么传入的时候，排在右边的`Gordian` 将会套在外层

```java
@Parasitic(gordians = {GordianImp.class, GordianImp2.class})
    public void test() throws InterruptedException {
        System.out.println("执行了-----test");
    }
```
![在这里插入图片描述](https://img-blog.csdnimg.cn/20200829175352389.png#pic_center)




# 3. 实现原理
和`lombok` 一样，`gordian` 将在代码的编译期 就去生成了对应的 代理代码

还是上面`test()` 那个列子，打开编译后的`class`文件可以看到生成的代码如下所示 , 为了方便注释，博主将代码复制下来
```java
public class Chy {
    public Chy() {
    }

    public void test() throws InterruptedException {
        //将业务代码复制到了 lambda 表达式中
        Parasitifer chyParasitifer_0 = () -> {
            System.out.println("执行了-----test");
            return null;
        };
        //把注解上面指定的实现类给 new了
        GordianImp gordianVar_0 = new GordianImp();

        try {
   	        //执行了 Lambda 中的代码
            gordianVar_0.control(chyParasitifer_0);
        } catch (Throwable var4) {
            if (var4 instanceof InterruptedException) {
                throw (InterruptedException)var4;
            } else {
                throw (RuntimeException)var4;
            }
        }
    }
}
```
是不是一看上面代码就一目了然了

# 4. 工厂模式
如果细心的读者看了上面生成的代码，可能会有一个小问题， `Gordian` 的实现类每次执行方法的时候都会去 `new` 一个新的对象，那么如何去让多个业务方法公用一个 `Gordian` 实现类嗯？

所以这里提供了`工厂模式`

将注解 `@Parasitic` 中的参数 `factoryMode = true` 即可

```java
public class GordianImp2 implements Gordian {
    Integer i = 0;
    @Override
    public Object control(Parasitifer parasitifer) throws Throwable {
        System.out.println("GordianImp2 执行开始 @@@@@@@@@@@@@@ --->" + i);
        Object result = parasitifer.exec();
        i++;
        System.out.println("GordianImp2 执行结束 ################");
        return result;
    }
}

```

这里定义一个新的 `GordianImp` , 里面有个 `i` 每次调用都会 自加

然后调用 `test()` , `test2()` 方法
```java
public class Chy {

    @Parasitic(gordians = {GordianImp2.class}, factoryMode = true)
    public void test() throws InterruptedException {
        System.out.println("执行了-----test");
    }

    @Parasitic(gordians = {GordianImp2.class}, factoryMode = true)
    public void test2() throws InterruptedException {
        System.out.println("执行了-----test2");
    }

    public static void main(String[] args) throws InterruptedException {
        Chy chy = new Chy();
        chy.test();
        chy.test2();
        chy.test();
    }
    
}
```
![在这里插入图片描述](https://img-blog.csdnimg.cn/20200829180315609.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3UwMTA5Mjg1ODk=,size_16,color_FFFFFF,t_70#pic_center)

##  4.1 自定义工厂
这里工厂是可以让使用者自定义的，你可以花式去创建`Gordian` 的实现对象

使用 `SPI`机制 来让你自定义工厂：
`META-INF.services` 文件夹下添加 `com.chy.gordian.factory.GordianFactory` 文件，里面写入你工厂实现类的全路径即可，哦对了自定义的工厂需要实现接口 `GordianFactory`, 然后重写里面的 `public Gordian createInstance(String gordianName)` 方法， 入参的`string` 将是 `Gordian` 实现类的类全路径

还有一个 `order()` 方法用来指定加载工厂的优先级，数字越小优先级越高。

## 4.1.1 spring的支持

所以你可以通过上面提到的`自定义工厂`机制来把 `GordianImp` 交给 `spring` 管理，这样你就可以在 `GordianImp` 上打上`@Component` 注解拥有`spring` 给与的一切能力,  笔者这久比较忙后面会支持这个功能
