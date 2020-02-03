/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.type

import org.intellij.lang.annotations.Language
import org.rust.ProjectDescriptor
import org.rust.RsTestBase
import org.rust.WithStdlibRustProjectDescriptor
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.types.cmt

class RsMemoryCategorizationTest : RsTestBase() {
    private fun testExpr(@Language("Rust") code: String, description: String = "") {
        InlineFile(code)
        check(description)
    }

    private fun check(description: String) {
        val (expr, expectedCategory) = findElementAndDataInEditor<RsExpr>()

        val cmt = expr.cmt
        val actual = "${cmt?.category?.javaClass?.simpleName}, ${cmt?.mutabilityCategory}"
        check(actual == expectedCategory) {
            "Category mismatch. Expected: $expectedCategory, found: $actual. $description"
        }
    }

    fun `test declared immutable`() = testExpr("""
        fn main() {
            let x = 42;
            x;
          //^ Local, Immutable
        }
    """)

    fun `test declared mutable`() = testExpr("""
        fn main() {
            let mut x = 42;
            x;
          //^ Local, Declared
        }
    """)

    fun `test declared immutable with immutable deref`() = testExpr("""
        fn main() {
            let y = 42;
            let x = &y;
            (*x);
             //^ Deref, Immutable
        }
    """)

    fun `test declared mutable with immutable deref`() = testExpr("""
        fn main() {
            let mut y = 42;
            let x = &y;
            (*x);
             //^ Deref, Immutable
        }
    """)

    fun `test declared mutable with mutable deref`() = testExpr("""
        fn main() {
            let mut y = 42;
            let x = &mut y;
            (*x);
             //^ Deref, Declared
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test immutable array`() = testExpr("""
        fn main() {
            let a: [i32; 3] = [0; 3];
            a[1];
             //^ Index, Immutable
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test mutable array`() = testExpr("""
        fn main() {
            let mut a: [i32; 3] = [0; 3];
            a[1];
             //^ Index, Inherited
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test slice`() = testExpr("""
        fn f(buf: &mut [u8]) {
            (buf[0]);
                 //^ Deref, Immutable
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test slice assign 1`() = testExpr("""
        fn f(buf: &mut [u8]) {
            buf[0] = 1;
               //^ Deref, Declared
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test slice assign 2`() = testExpr("""
        fn f(buf: &mut [u8]) {
            (buf[0]) = 1;
                //^ Deref, Declared
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test slice assign 3`() = testExpr("""
        fn f(buf: &mut [&mut [u8]]) {
            buf[0][0] = 1;
               //^ Deref, Declared
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test slice mut 4`() = testExpr("""
        fn f(buf: &mut [u8]) {
            let _ = &mut (buf[0]);
                       //^ Deref, Declared
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test slice mut 5`() = testExpr("""
        fn f(buf: &mut [u8]) {
            let ref mut a = (buf[0]);
                          //^ Deref, Declared
        }
    """)

    // TODO test let ref mut, &mut

    fun `test immutable struct`() = testExpr("""
        struct Foo { a: i32 }
        fn main() {
            let x = Foo { a: 1 };
            (x.a);
              //^ Field, Immutable
        }
    """)

    fun `test mutable struct`() = testExpr("""
        struct Foo { a: i32 }
        fn main() {
            let mut x = Foo { a: 1 };
            (x.a);
              //^ Field, Inherited
        }
    """)

    fun `test mutable struct with immutable reference`() = testExpr("""
        struct Foo { a: i32 }
        fn main() {
            let mut foo = Foo { a: 1 };
            let x = &foo;
            (x.a);
              //^ Field, Immutable
        }
    """)

    fun `test mutable struct with mutable reference`() = testExpr("""
        struct Foo { a: i32 }
        fn main() {
            let mut foo = Foo { a: 1 };
            let x = &mut foo;
            (x.a);
              //^ Field, Inherited
        }
    """)

    fun `test mutable struct with multiple references to immutable reference`() = testExpr("""
        struct Foo { a: i32 }
        fn main() {
            let mut foo = Foo { a: 1 };
            let x = &mut &mut &foo;
            (x.a);
              //^ Field, Immutable
        }
    """)

    fun `test mutable struct with multiple references to mutable reference`() = testExpr("""
        struct Foo { a: i32 }
        fn main() {
            let mut foo = Foo { a: 1 };
            let x = & & &mut foo;
            (x.a);
              //^ Field, Inherited
        }
    """)

    fun `test const raw pointer`() = testExpr("""
        struct Foo { a: i32 }
        fn main() {
            let x = 5;
            let p = &x as *const i32;
            (*p);
             //^ Deref, Immutable
        }
    """)

    fun `test mut raw pointer`() = testExpr("""
        struct Foo { a: i32 }
        fn main() {
            let mut x = 5;
            let p = &mut x as *mut i32;
            (*p);
             //^ Deref, Declared
        }
    """)

    fun `test immutable self`() = testExpr("""
        struct Foo {}
        impl Foo {
            fn f(&self) {
                self;
                 //^ Local, Immutable
            }
        }
    """)

    fun `test mutable self`() = testExpr("""
        struct Foo {}
        impl Foo {
            fn f(&mut self) {
                self;
                 //^ Local, Declared
            }
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test static`() = testExpr("""
        static N: i32 = 42;
        fn main() {
            N;
          //^ StaticItem, Immutable
        }
    """)

    fun `test const`() = testExpr("""
        const N: i32 = 42;
        fn main() {
            N;
          //^ Rvalue, Declared
        }
    """)

    fun `test rvalue method call`() = testExpr("""
        fn main() {
          let v = vec![1];
          v.iter();
               //^ Rvalue, Declared
        }
    """)

    fun `test rvalue if expr`() = testExpr("""
        struct S {}
        fn main() {
          (if true { S } else { S });
                                 //^ Rvalue, Declared
        }
    """)

    fun `test rvalue closure`() = testExpr("""
        fn main() {
          (|x: i32| x + 1);
                       //^ Rvalue, Declared
        }
    """)

    fun `test immutable closure parameter`() = testExpr("""
        fn main() {
          (|x: i32| x + 1);
                  //^ Local, Immutable
        }
    """)

    fun `test mutable closure parameter`() = testExpr("""
        fn main() {
          (|mut x: i32| x + 1);
                      //^ Local, Declared
        }
    """)

    fun `test overloadable operator with borrow adjustment`() = testExpr("""
        fn main() {
            let mut x = 0;
            x += 1;
          //^ Rvalue, Declared
        }
    """)

    fun `test overloadable operator with inconsistent borrow adjustment`() = testExpr("""
        fn main() {
            let x = 0;
            x += 1;
          //^ Local, Immutable
        }
    """)
}
