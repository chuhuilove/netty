/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel;

import io.netty.util.Attribute;
import io.netty.util.AttributeKey;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Handles an I/O event or intercepts an I/O operation, and forwards it to its next handler in
 * its {@link ChannelPipeline}.
 *
 * 处理一个I/O消息或拦截一个I/O操作,并将其转发到其{@link ChannelPipeline}中的下一个处理程序.
 *
 * <h3>子类型</h3>
 * <p>
 * {@link ChannelHandler} itself does not provide many methods, but you usually have to implement one of its subtypes:
 * {@link ChannelHandler} 它自己不提供任何方法,但是你通常不得不实现它的一个子类型:
 * <ul>
 * <li>{@link ChannelInboundHandler} 处理入站I/O事件,</li>
 * <li>{@link ChannelOutboundHandler} 处理出站I/O操作.</li>
 * </ul>
 * </p>
 * <p>
 * Alternatively, the following adapter classes are provided for your convenience:
 * 另外,下面的适配类为你提供方便:
 * <ul>
 * <li>{@link ChannelInboundHandlerAdapter} 处理入站I/O事件,</li>
 * <li>{@link ChannelOutboundHandlerAdapter} 处理出站I/O操作,</li>
 * <li>{@link ChannelDuplexHandler} 处理入站和出站事件</li>
 * </ul>
 * </p>
 * <p>
 * 更多信息,请参考每个子类型的文档.
 * </p>
 *
 * <h3>The context object上下文对象</h3>
 * <p>
 * A {@link ChannelHandler} is provided with a {@link ChannelHandlerContext}
 * object.
 * {@link ChannelHandler}提供{@link ChannelHandlerContext}对象.
 *
 * A {@link ChannelHandler} is supposed to interact with the
 * {@link ChannelPipeline} it belongs to via a context object.
 *
 * {@link ChannelHandler}应该通过上下文对象与它所属的{@link ChannelPipeline}进行交互.
 *
 * Using the
 * context object, the {@link ChannelHandler} can pass events upstream or
 * downstream, modify the pipeline dynamically, or store the information
 * (using {@link AttributeKey}s) which is specific to the handler.
 *
 * 使用上下文对象,{@link ChannelHandler}可以传递上游或下游事件,动态修改管道,或存储特定于处理程序的信息(使用{@link AttributeKey}).
 *
 * <h3>State management状态管理</h3>
 *
 * {@link ChannelHandler} 经常需要存储一些状态信息.
 * 最简单和推荐的方法是使用成员变量:
 * <pre>
 * public interface Message {
 *     // 这里是你的方法
 * }
 *
 * public class DataServerHandler extends {@link SimpleChannelInboundHandler}&lt;Message&gt; {
 *
 *     <b>private boolean loggedIn;</b>
 *
 *     {@code @Override}
 *     public void channelRead0({@link ChannelHandlerContext} ctx, Message message) {
 *         if (message instanceof LoginMessage) {
 *             authenticate((LoginMessage) message);
 *             <b>loggedIn = true;</b>
 *         } else (message instanceof GetDataMessage) {
 *             if (<b>loggedIn</b>) {
 *                 ctx.writeAndFlush(fetchSecret((GetDataMessage) message));
 *             } else {
 *                 fail();
 *             }
 *         }
 *     }
 *     ...
 * }
 * </pre>
 * Because the handler instance has a state variable which is dedicated to
 * one connection, you have to create a new handler instance for each new
 * channel to avoid a race condition where a unauthenticated client can get
 * the confidential information:
 * 因为handler实例具有专用于一个连接的状态变量,
 * 所以必须为每个新channel创建一个新的handler实例,
 * 以避免不可靠的客户端可以获取机密信息的竞争条件:
 * <pre>
 * // 为每个channel创建一个新的handler实例.
 * // See {@link ChannelInitializer#initChannel(Channel)}.
 * public class DataServerInitializer extends {@link ChannelInitializer}&lt;{@link Channel}&gt; {
 *     {@code @Override}
 *     public void initChannel({@link Channel} channel) {
 *         channel.pipeline().addLast("handler", <b>new DataServerHandler()</b>);
 *     }
 * }
 *
 * </pre>
 *
 * <h4>Using {@link AttributeKey}s</h4>
 *
 * Although it's recommended to use member variables to store the state of a
 * handler, for some reason you might not want to create many handler instances.
 * 尽管建议使用成员变量来存储handler的状态,但处于某种原因,你可能不想创建许多handler实例.
 *
 * 在这种情况下,你可以使用由{@link ChannelHandlerContext}提供的{@link AttributeKey}:
 * <pre>
 * public interface Message {
 *     // your methods here
 * }
 *
 * {@code @Sharable}
 * public class DataServerHandler extends {@link SimpleChannelInboundHandler}&lt;Message&gt; {
 *     private final {@link AttributeKey}&lt;{@link Boolean}&gt; auth =
 *           {@link AttributeKey#valueOf(String) AttributeKey.valueOf("auth")};
 *
 *     {@code @Override}
 *     public void channelRead({@link ChannelHandlerContext} ctx, Message message) {
 *         {@link Attribute}&lt;{@link Boolean}&gt; attr = ctx.attr(auth);
 *         if (message instanceof LoginMessage) {
 *             authenticate((LoginMessage) o);
 *             <b>attr.set(true)</b>;
 *         } else (message instanceof GetDataMessage) {
 *             if (<b>Boolean.TRUE.equals(attr.get())</b>) {
 *                 ctx.writeAndFlush(fetchSecret((GetDataMessage) o));
 *             } else {
 *                 fail();
 *             }
 *         }
 *     }
 *     ...
 * }
 * </pre>
 * 现在handler的状态信息已经附加到了{@link ChannelHandlerContext}上,你可以添加相同的handler实例到不同的pipeline.
 * <pre>
 * public class DataServerInitializer extends {@link ChannelInitializer}&lt;{@link Channel}&gt; {
 *
 *     private static final DataServerHandler <b>SHARED</b> = new DataServerHandler();
 *
 *     {@code @Override}
 *     public void initChannel({@link Channel} channel) {
 *         channel.pipeline().addLast("handler", <b>SHARED</b>);
 *     }
 * }
 * </pre>
 *
 *
 * <h4>The {@code @Sharable} annotation</h4>
 * <p>
 * 在上面使用{@link AttributeKey}的示例中,你可能已经注意到{@code @Sharable}注解.
 * <p>
 * If a {@link ChannelHandler} is annotated with the {@code @Sharable}
 * annotation, it means you can create an instance of the handler just once and
 * add it to one or more {@link ChannelPipeline}s multiple times without
 * a race condition.
 *
 * 如果{@link ChannelHandler}使用{@code @Sharable}注解进行标注,
 * 则意味着你只需创建一次handler实例,并可以将其多次添加到一个或多个{@link ChannelPipeline}中,而不会出现竞争条件.
 *
 * <p>
 * If this annotation is not specified, you have to create a new handler
 * instance every time you add it to a pipeline because it has unshared state
 * such as member variables.
 *
 * 如果未指定此注解,则每次将其添加到pipeline时都必须创建新的handler实例,因为它像成员变量那样,具有非共享状态.
 *
 * <p>
 * This annotation is provided for documentation purpose, just like
 * <a href="http://www.javaconcurrencyinpractice.com/annotations/doc/">the JCIP annotations</a>.
 *
 * <h3>Additional resources worth reading</h3>
 * <p>
 * Please refer to the {@link ChannelHandler}, and
 * {@link ChannelPipeline} to find out more about inbound and outbound operations,
 * what fundamental differences they have, how they flow in a  pipeline,  and how to handle
 * the operation in your application.
 */
public interface ChannelHandler {

    /**
     * Gets called after the {@link ChannelHandler} was added to the actual context and it's ready to handle events.
     *
     * 在将{@link ChannelHandler}添加到实际上下文并准备处理事件后调用.
     */
    void handlerAdded(ChannelHandlerContext ctx) throws Exception;

    /**
     * Gets called after the {@link ChannelHandler} was removed from the actual context and it doesn't handle events
     * anymore.
     * 从实际上下文中删除{@link ChannelHandler}后调用,调用后,该handler不再处理事件.
     */
    void handlerRemoved(ChannelHandlerContext ctx) throws Exception;

    /**
     * 如果抛出{@link Throwable},则调用
     *
     * @deprecated if you want to handle this event you should implement {@link ChannelInboundHandler} and
     * implement the method there.
     */
    @Deprecated
    void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception;

    /**
     * Indicates that the same instance of the annotated {@link ChannelHandler}
     * can be added to one or more {@link ChannelPipeline}s multiple times
     * without a race condition.
     * <p>
     * If this annotation is not specified, you have to create a new handler
     * instance every time you add it to a pipeline because it has unshared
     * state such as member variables.
     * <p>
     * This annotation is provided for documentation purpose, just like
     * <a href="http://www.javaconcurrencyinpractice.com/annotations/doc/">the JCIP annotations</a>.
     */
    @Inherited
    @Documented
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Sharable {
        // no value
    }
}
